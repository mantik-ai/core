package ai.mantik.planner.impl.exec

import ai.mantik.ds.element.Bundle
import ai.mantik.executor.Executor
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.DockerConfig
import ai.mantik.planner._
import ai.mantik.planner.impl.FutureHelper
import ai.mantik.planner.repository.{ DeploymentInfo, FileRepository, MantikArtifact, Repository }
import ai.mantik.planner.utils.{ AkkaRuntime, ComponentBase }
import akka.http.scaladsl.model.Uri
import org.slf4j.LoggerFactory
import io.circe.syntax._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }

/** Responsible for executing plans. */
private[impl] class PlanExecutorImpl(
    fileRepository: FileRepository,
    repository: Repository, executor: Executor,
    isolationSpace: String,
    dockerConfig: DockerConfig
)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with PlanExecutor {

  private val logger = LoggerFactory.getLogger(getClass)
  logger.info(s"Initializing PlanExecutor")

  private val jobTimeout = Duration.fromNanos(config.getDuration("mantik.planner.jobTimeout").toNanos)
  private val jobPollInterval = Duration.fromNanos(config.getDuration("mantik.planner.jobPollInterval").toNanos)
  private val fileCache = new FileCache()

  private val openFilesBuilder = new ExecutionOpenFilesBuilder(fileRepository, fileCache)

  def execute[T](plan: Plan[T]): Future[T] = {
    for {
      _ <- prepareKubernetesFileServiceResult
      openFiles <- FutureHelper.time(logger, "Open Files") {
        openFilesBuilder.openFiles(plan.cacheGroups, plan.files)
      }
      result <- executeOp(plan.op)(openFiles)
    } yield result.asInstanceOf[T]
  }

  private lazy val prepareKubernetesFileServiceResult = FutureHelper.time(logger, "Prepare File Service") {
    val kubernetesName = config.getString("mantik.core.fileServiceKubernetesName")
    val address = fileRepository.address()
    val request = PublishServiceRequest(
      isolationSpace = isolationSpace,
      serviceName = kubernetesName,
      port = address.getPort,
      externalName = address.getAddress.getHostAddress,
      externalPort = address.getPort
    )
    logger.debug(s"Preparing FileService ${request.asJson}")
    executor.publishService(request).map { response =>
      logger.info(s"FileService is published with ${response.name}")
      Uri(s"http://${response.name}") // is of format domain:port
    }
  }

  /** Access to the URI of the FileService.*/
  private def fileServiceUri: Uri = Await.result(prepareKubernetesFileServiceResult, Duration.Inf)

  def executeOp(planOp: PlanOp)(implicit files: ExecutionOpenFiles): Future[Any] = {
    planOp match {
      case PlanOp.Sequential(parts) =>
        FutureHelper.time(logger, s"Running ${parts.length} sub tasks") {
          FutureHelper.afterEachOtherStateful(parts, "empty start": Any) {
            case (_, part) =>
              executeOp(part)
          }
        }
      case PlanOp.Empty =>
        logger.debug(s"Executing empty")
        Future.successful(())
      case PlanOp.PushBundle(bundle, fileRef) =>
        val fileId = files.resolveFileId(fileRef)
        FutureHelper.time(logger, s"Bundle Push $fileId") {
          fileRepository.storeFile(fileId, FileRepository.MantikBundleContentType).flatMap { sink =>
            val source = bundle.encode(withHeader = true)
            source.runWith(sink)
          }
        }
      case PlanOp.PullBundle(_, fileRef) =>
        val fileId = files.resolveFileId(fileRef)
        FutureHelper.time(logger, s"Bundle Pull $fileId") {
          fileRepository.loadFile(fileId).flatMap { source =>
            val sink = Bundle.fromStreamWithHeader()
            source.runWith(sink)
          }
        }
      case PlanOp.AddMantikItem(id, item, fileReference) =>
        // TODO: Support for adding without Id.
        val fileId = fileReference.map(files.resolveFileId)
        val mantikfile = item.mantikfile
        val artifact = MantikArtifact(mantikfile, fileId, id, item.itemId)
        FutureHelper.time(logger, s"Adding Mantik Item $id") {
          repository.store(artifact).map { _ =>
            item.state.update { state =>
              state.copy(
                isStored = true,
                mantikId = Some(id)
              )
            }
          }
        }
      case PlanOp.RunGraph(graph) =>
        val jobGraphConverter = new JobGraphConverter(fileServiceUri, isolationSpace, files, dockerConfig.logins)
        val job = jobGraphConverter.translateGraphIntoJob(graph)
        FutureHelper.time(logger, s"Executing Job in isolationSpace ${job.isolationSpace}") {
          executeJobAndWaitForReady(job)
        }
      case cacheOp: PlanOp.CacheOp =>
        if (files.cacheHits.contains(cacheOp.cacheGroup)) {
          logger.debug("Skipping op, because of cache hit")
          Future.successful(())
        } else {
          logger.debug("Executing op, cache miss")
          executeOp(cacheOp.alternative).map { _ =>
            cacheOp.files.foreach {
              case (cacheKey, fileRef) =>
                val resolved = files.resolveFileId(fileRef)
                fileCache.add(cacheKey, resolved)
                ()
            }
          }
        }
      case da: PlanOp.DeployAlgorithm =>
        FutureHelper.time(logger, s"Deploying Algorithm ${da.item.mantikId} -> ${da.serviceId}/${da.serviceNameHint}") {
          val jobGraphConverter = new JobGraphConverter(fileServiceUri, isolationSpace, files, dockerConfig.logins)
          val deployServiceRequest = jobGraphConverter.createDeployServiceRequest(da.serviceId, da.serviceNameHint, da.container)

          for {
            response <- executor.deployService(deployServiceRequest)
            deploymentState = DeploymentState(name = response.serviceName, url = response.url)
            _ = da.item.state.update(_.copy(deployment = Some(deploymentState: DeploymentState)))
            deploymentInfo = DeploymentInfo(
              name = deploymentState.name,
              url = deploymentState.url,
              timestamp = akkaRuntime.clock.instant()
            )
            _ <- repository.setDeploymentInfo(da.item.itemId, Some(deploymentInfo))
          } yield {
            deploymentState
          }
        }
      case c: PlanOp.Const =>
        Future.successful(c.value)
    }
  }

  private def executeJobAndWaitForReady(job: Job): Future[Unit] = {
    executor.schedule(job).flatMap { jobId =>
      FutureHelper.tryMultipleTimes(jobTimeout, jobPollInterval) {
        executor.status(job.isolationSpace, jobId).map { status =>
          Option(status.state).filter { state =>
            state == JobState.Failed || state == JobState.Finished
          }
        }
      }.flatMap {
        case JobState.Finished =>
          logger.debug(s"Job $jobId finished successfully")
          Future.successful(())
        case JobState.Failed =>
          logger.debug(s"Job $jobId failed, trying to get logs")
          executor.logs(job.isolationSpace, jobId).flatMap { logs =>
            // TODO: Nicer exception, base class.
            throw new RuntimeException(s"Job failed, $logs")
          }
        case other =>
          // should not come here, is filtered out above
          // TODO: Nicer exception, base class.
          throw new RuntimeException(s"Unexpected job state $other")
      }
    }
  }
}

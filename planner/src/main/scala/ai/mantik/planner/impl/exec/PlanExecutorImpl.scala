package ai.mantik.planner.impl.exec

import java.net.InetSocketAddress

import ai.mantik
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.ds.element.Bundle
import ai.mantik.elements.NamedMantikId
import ai.mantik.executor.Executor
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.DockerConfig
import ai.mantik.planner
import ai.mantik.planner.Planner.InconsistencyException
import ai.mantik.planner._
import ai.mantik.planner.pipelines.PipelineRuntimeDefinition
import ai.mantik.planner.repository.{ DeploymentInfo, FileRepository, FileRepositoryServer, MantikArtifact, MantikArtifactRetriever, Repository }
import akka.http.scaladsl.model.Uri
import io.circe.syntax._
import javax.inject.{ Inject, Named, Singleton }

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.Success

/** Responsible for executing plans. */
private[planner] class PlanExecutorImpl(
    fileRepository: FileRepository,
    repository: Repository, executor: Executor,
    isolationSpace: String,
    dockerConfig: DockerConfig,
    artifactRetriever: MantikArtifactRetriever,
    fileCache: FileCache,
    clientConfig: ClientConfig
)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with PlanExecutor {

  @Singleton
  @Inject
  def this(fileRepository: FileRepository, repository: Repository, executor: Executor, retriever: MantikArtifactRetriever, fileCache: FileCache, clientConfig: ClientConfig)(implicit akkaRuntime: AkkaRuntime) {
    this(
      fileRepository,
      repository,
      executor,
      isolationSpace = akkaRuntime.config.getString("mantik.planner.isolationSpace"),
      dockerConfig = DockerConfig.parseFromConfig(
        akkaRuntime.config.getConfig("mantik.bridge.docker")
      ),
      retriever,
      fileCache,
      clientConfig
    )
  }

  private val jobTimeout = Duration.fromNanos(config.getDuration("mantik.planner.jobTimeout").toNanos)
  private val jobPollInterval = Duration.fromNanos(config.getDuration("mantik.planner.jobPollInterval").toNanos)

  private val openFilesBuilder = new ExecutionOpenFilesBuilder(fileRepository, fileCache)

  def execute[T](plan: Plan[T]): Future[T] = {
    for {
      openFiles <- FutureHelper.time(logger, "Open Files") {
        openFilesBuilder.openFiles(plan.cacheGroups, plan.files)
      }
      memory = new Memory()
      result <- executeOp(plan.op)(openFiles, memory)
    } yield result.asInstanceOf[T]
  }

  override def cachedFile(cacheKey: CacheKey): Option[String] = {
    fileCache.get(cacheKey)
  }

  def executeOp[T](planOp: PlanOp[T])(implicit files: ExecutionOpenFiles, memory: Memory): Future[T] = {
    executeOpInner(planOp).andThen {
      case Success(value) => memory.setLast(value)
    }
  }

  private def executeOpInner[T](planOp: PlanOp[T])(implicit files: ExecutionOpenFiles, memory: Memory): Future[T] = {
    planOp match {
      case PlanOp.Sequential(parts, last) =>
        FutureHelper.time(logger, s"Running ${parts.length} sub tasks") {
          FutureHelper.afterEachOtherStateful(parts, memory.getLastOrNull()) {
            case (_, part) =>
              executeOp(part)
          }
        }.flatMap { _ =>
          FutureHelper.time(logger, s"Running last part") {
            executeOp(last)
          }
        }
      case PlanOp.Empty =>
        logger.debug(s"Executing empty")
        Future.successful(())
      case PlanOp.StoreBundleToFile(bundle, fileRef) =>
        val fileId = files.resolveFileId(fileRef)
        FutureHelper.time(logger, s"Bundle Push $fileId") {
          fileRepository.storeFile(fileId, FileRepository.MantikBundleContentType).flatMap { sink =>
            val source = bundle.encode(withHeader = true)
            source.runWith(sink)
          }
        }
      case PlanOp.LoadBundleFromFile(_, fileRef) =>
        val fileId = files.resolveFileId(fileRef)
        FutureHelper.time(logger, s"Bundle Pull $fileId") {
          fileRepository.loadFile(fileId).flatMap {
            case (_, source) =>
              val sink = Bundle.fromStreamWithHeader()
              source.runWith(sink)
          }
        }
      case PlanOp.AddMantikItem(item, fileReference) =>
        val fileId = fileReference.map(files.resolveFileId)
        val mantikfile = item.mantikfile
        val id = item.itemId
        val namedId = item.name
        val artifact = MantikArtifact(mantikfile, fileId, namedId, item.itemId)
        FutureHelper.time(logger, s"Adding Mantik Item $id") {
          repository.store(artifact).andThen {
            case Success(_) =>
              item.state.update { state =>
                state.copy(
                  itemStored = true,
                  nameStored = namedId.isDefined,
                  namedMantikItem = namedId
                )
              }
          }
        }
      case PlanOp.TagMantikItem(item, id) =>
        FutureHelper.time(logger, s"Tagging Mantik Item") {
          repository.ensureMantikId(item.itemId, id).andThen {
            case Success(_) =>
              item.state.update { state =>
                state.copy(
                  namedMantikItem = Some(id)
                )
              }
          }.map(_ => ())
        }
      case PlanOp.PushMantikItem(item) =>
        if (!item.state.get.itemStored) {
          throw new InconsistencyException("Item is not stored")
        }
        val mantikId = item.mantikId
        FutureHelper.time(logger, s"Pushing Artifact ${mantikId}") {
          artifactRetriever.push(mantikId)
        }.map { _ => () }
      case PlanOp.RunGraph(graph) =>
        val jobGraphConverter = new JobGraphConverter(clientConfig.remoteFileRepositoryAddress, isolationSpace, files, dockerConfig.logins)
        val job = jobGraphConverter.translateGraphIntoJob(graph)
        logger.debug(s"Running job ${jobDebugString(job)}")
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
          val jobGraphConverter = new JobGraphConverter(clientConfig.remoteFileRepositoryAddress, isolationSpace, files, dockerConfig.logins)
          val deployServiceRequest = jobGraphConverter.createDeployServiceRequest(da.serviceId, da.serviceNameHint, da.container)

          for {
            response <- executor.deployService(deployServiceRequest)
            deploymentState = DeploymentState(name = response.serviceName, internalUrl = response.url)
            _ = da.item.state.update(_.copy(deployment = Some(deploymentState)))
            deploymentInfo = DeploymentInfo(
              name = deploymentState.name,
              internalUrl = deploymentState.internalUrl,
              timestamp = akkaRuntime.clock.instant()
            )
            _ <- repository.setDeploymentInfo(da.item.itemId, Some(deploymentInfo))
          } yield {
            deploymentState
          }
        }
      case dp: PlanOp.DeployPipeline =>
        FutureHelper.time(logger, s"Deploying Pipeline ${dp.item.mantikId}/${dp.item.itemId}") {
          // TODO: It would be better if the Planner would generate the runtime definition
          // but it doesn't has access to the internal URLs until the moment all sub components are deployed.
          // See issue #94.
          val runtimeDefinition = buildPipelineRuntimeDefinition(dp).asJson

          val request = DeployServiceRequest(
            serviceId = dp.serviceId,
            nameHint = dp.serviceNameHint,
            isolationSpace = isolationSpace,
            service = DeployableService.Pipeline(runtimeDefinition),
            extraLogins = dockerConfig.logins,
            ingress = dp.ingress
          )
          for {
            response <- executor.deployService(request)
            deploymentState = DeploymentState(name = response.serviceName, internalUrl = response.url, externalUrl = response.externalUrl)
            _ = dp.item.state.update(_.copy(deployment = Some(deploymentState)))
            deploymentInfo = DeploymentInfo(
              name = deploymentState.name,
              internalUrl = deploymentState.internalUrl,
              timestamp = akkaRuntime.clock.instant()
            )
            _ <- repository.setDeploymentInfo(dp.item.itemId, Some(deploymentInfo))
          } yield {
            deploymentState
          }
        }
      case c: PlanOp.Const[T] =>
        Future.successful(c.value)
      case c: PlanOp.MemoryReader[T] =>
        Future.successful(memory.get(c.memoryId).asInstanceOf[T])
      case c: PlanOp.MemoryWriter[T] =>
        val last = memory.getLast().asInstanceOf[T]
        memory.put(c.memoryId, last)
        Future.successful(last)
    }
  }

  private def buildPipelineRuntimeDefinition(dp: PlanOp.DeployPipeline): PipelineRuntimeDefinition = {
    def extractStep(algorithm: Algorithm): PipelineRuntimeDefinition.Step = {
      val url = algorithm.state.get.deployment match {
        case None    => throw new planner.Planner.InconsistencyException("Required sub algorithm not deployed")
        case Some(d) => d.internalUrl
      }
      val applyResource = "apply"
      val fullUrl = Uri(applyResource).resolvedAgainst(url).toString()
      val resultType = algorithm.functionType.output
      PipelineRuntimeDefinition.Step(
        fullUrl,
        resultType
      )
    }
    val steps = dp.steps.map(extractStep)
    val inputType = dp.item.functionType.input
    PipelineRuntimeDefinition(
      name = dp.item.mantikId.toString,
      steps,
      inputType = inputType
    )
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

  private def jobDebugString(job: Job): String = {
    def formatNode(node: NodeService): String = {
      node match {
        case c: ContainerService => s"Container: ${c.main.image}"
        case e: ExistingService  => s"Existing:  ${e.url}"
      }
    }
    try {
      val graph = job.graph
      val analysis = new GraphAnalysis(graph)
      val builder = StringBuilder.newBuilder
      builder ++= s"Job ${job.isolationSpace}\n"
      analysis.flows.zipWithIndex.foreach {
        case (flow, idx) =>
          builder ++= s"  - Flow ${idx}\n"
          flow.nodes.foreach { nodeResourceRef =>
            val resolved = graph.resolveReference(nodeResourceRef)
            resolved match {
              case Some((node, res)) => builder ++= s"    - - ${formatNode(node.service)} : ${nodeResourceRef.resource} (${res.resourceType}/${res.contentType})\n"
              case _                 => builder ++= s"    -- Unresolved ${nodeResourceRef}" // should not happen
            }
          }
      }
      builder.result()
    } catch {
      case e: GraphAnalysis.AnalyzerException =>
        s"Invalid job ${e}"
    }

  }
}

package ai.mantik.planner.impl.exec

import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.executor.Executor
import ai.mantik.executor.model._
import ai.mantik.planner.PlanNodeService.DockerContainer
import ai.mantik.planner._
import ai.mantik.planner.impl.FutureHelper
import ai.mantik.repository.{ FileRepository, MantikArtifact, Repository }
import akka.actor.ActorSystem
import akka.stream.Materializer
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

/** Responsible for executing plans. */
private[impl] class PlanExecutorImpl(
    fileRepository: FileRepository,
    repository: Repository, executor: Executor,
    isolationSpace: String,
    contentType: String
)(implicit ec: ExecutionContext, actorSystem: ActorSystem, materializer: Materializer) extends PlanExecutor {

  private val logger = LoggerFactory.getLogger(getClass)
  logger.info(s"Initializing PlanExecutor")

  def execute(plan: Plan): Future[Any] = {
    FutureHelper.time(logger, "Open Files")(ExecutionOpenFiles.openFiles(fileRepository, plan.files)).flatMap { implicit fileContext =>
      executeOp(plan.op)
    }
  }

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
      case PlanOp.AddMantikItem(id, fileReference, mantikfile) =>
        val fileId = fileReference.map(files.resolveFileId)
        val artifact = MantikArtifact(mantikfile, fileId, id)
        FutureHelper.time(logger, s"Adding Mantik Item $id") {
          repository.store(artifact)
        }
      case PlanOp.RunGraph(graph) =>
        val jobGraphConverter = new JobGraphConverter(isolationSpace, files, contentType)
        val job = jobGraphConverter.translateGraphIntoJob(graph)
        FutureHelper.time(logger, s"Executing Job in isolationSpace ${job.isolationSpace}") {
          executeJobAndWaitForReady(job)
        }
    }
  }

  private def executeJobAndWaitForReady(job: Job): Future[Unit] = {
    // TODO: Fixme
    val jobTimeout = 60.seconds
    val tryAgain = 1.second

    executor.schedule(job).flatMap { jobId =>
      FutureHelper.tryMultipleTimes(jobTimeout, tryAgain) {
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
package ai.mantik.core.impl

import java.time.Clock
import java.time.temporal.ChronoUnit

import ai.mantik.core.{ Plan, PlanExecutor }
import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import akka.actor.ActorSystem
import akka.stream.Materializer
import ai.mantik.executor.Executor
import ai.mantik.executor.model.{ Job, JobState }
import ai.mantik.repository.{ FileRepository, Repository }
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContext, Future, Promise, TimeoutException }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

/** Responsible for executing plans. */
private[impl] class PlanExecutorImpl(fileRepository: FileRepository, repository: Repository, executor: Executor)(implicit ec: ExecutionContext, actorSystem: ActorSystem, materializer: Materializer) extends PlanExecutor {

  private val logger = LoggerFactory.getLogger(getClass)
  logger.info(s"Initializing PlanExecutor")

  def execute(plan: Plan): Future[Any] = {
    plan match {
      case Plan.Sequential(parts) =>
        time(s"Running ${parts.length} sub tasks") {
          afterEachOther(parts, execute)
        }
      case Plan.Empty =>
        logger.debug(s"Executing empty")
        Future.successful(())
      case Plan.PushBundle(bundle, fileId) =>
        time(s"Bundle Push ${fileId}") {
          fileRepository.storeFile(fileId, FileRepository.MantikBundleContentType).flatMap { sink =>
            val source = bundle.encode(withHeader = true)
            source.runWith(sink)
          }
        }
      case Plan.PullBundle(dataType: DataType, fileId) =>
        time(s"Bundle Pull ${fileId}") {
          fileRepository.loadFile(fileId).flatMap { source =>
            val sink = Bundle.fromStreamWithHeader()
            source.runWith(sink)
          }
        }
      case Plan.AddMantikItem(item) =>
        time(s"Adding Mantik Item ${item.id}") {
          repository.store(item)
        }
      case Plan.RunJob(job) =>
        time(s"Executing Job in isolationSpace ${job.isolationSpace}") {
          executeJobAndWaitForReady(job)
        }
    }
  }

  private def time[T](name: String)(f: => Future[T]): Future[T] = {
    logger.debug(s"Executing ${name}")
    val t0 = System.currentTimeMillis()
    f.andThen {
      case Success(s) =>
        val t1 = System.currentTimeMillis()
        logger.debug(s"Finished executing ${name}, it took ${t1 - t0}ms")
      case Failure(e) =>
        val t1 = System.currentTimeMillis()
        logger.warn(s"Execution of ${name} failed within ${t1 - t0}ms: ${e.getMessage}")
    }
  }

  private def afterEachOther[T](in: Seq[T], f: T => Future[Any]): Future[Any] = {
    val result = Promise[Any]
    def continueRunning(pending: List[T], lastResult: Any): Unit = {
      pending match {
        case head :: tail =>
          f(head).andThen {
            case Success(s) => continueRunning(tail, s)
            case Failure(e) => result.tryFailure(e)
          }
        case Nil =>
          result.trySuccess(lastResult)
      }
    }

    continueRunning(in.toList, null)
    return result.future
  }

  private def executeJobAndWaitForReady(job: Job): Future[Unit] = {
    // TODO: Fixme
    val jobTimeout = 60.seconds
    val tryAgain = 1.second

    executor.schedule(job).flatMap { jobId =>
      tryMultipleTimes(jobTimeout, tryAgain) {
        executor.status(job.isolationSpace, jobId).map { status =>
          Option(status.state).filter { state =>
            state == JobState.Failed || state == JobState.Finished
          }
        }
      }.flatMap {
        case JobState.Finished =>
          logger.debug(s"Job ${jobId} finished successfully")
          Future.successful(())
        case JobState.Failed =>
          logger.debug(s"Job ${jobId} failed, trying to get logs")
          executor.logs(job.isolationSpace, jobId).flatMap { logs =>
            // TODO: Nicer exception, base class.
            throw new RuntimeException(s"Job failed, ${logs}")
          }
        case other =>
          // should not come here, is filtered out above
          // TODO: Nicer exception, base class.
          throw new RuntimeException(s"Unexpected job state ${other}")
      }
    }
  }

  /**
   * Try `f` multiple times within a given timeout.
   * TODO: This function is copy and paste from executor
   */
  private def tryMultipleTimes[T](timeout: FiniteDuration, tryAgainWaitDuration: FiniteDuration)(f: => Future[Option[T]]): Future[T] = {
    val result = Promise[T]
    val clock = Clock.systemUTC()
    val finalTimeout = clock.instant().plus(timeout.toMillis, ChronoUnit.MILLIS)
    def tryAgain(): Unit = {
      f.andThen {
        case Success(None) =>
          if (clock.instant().isAfter(finalTimeout)) {
            result.tryFailure(new TimeoutException(s"Timeout after ${timeout}"))
          } else {
            actorSystem.scheduler.scheduleOnce(tryAgainWaitDuration)(tryAgain())
          }
        case Success(Some(x)) =>
          result.trySuccess(x)
        case Failure(e) =>
          result.tryFailure(e)
      }
    }
    tryAgain()
    result.future
  }
}

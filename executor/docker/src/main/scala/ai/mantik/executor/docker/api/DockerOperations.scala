package ai.mantik.executor.docker.api

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.docker.api.DockerClient.WrappedErrorResponse
import ai.mantik.executor.docker.api.structures.ContainerWaitResponse
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

/** High level docker operations, consisting of multiple sub operations. */
class DockerOperations(dockerClient: DockerClient)(implicit akkaRuntime: AkkaRuntime) {
  import ai.mantik.componently.AkkaHelper._
  val logger = Logger(getClass)

  /** Execute a Pull policy for an image. */
  def executePullPolicy(pullPolicy: PullPolicy, image: String): Future[Unit] = {
    pullPolicy match {
      case PullPolicy.Never => Future.successful(())
      case PullPolicy.IfNotPresent =>
        pullImageIfNotPresent(image)
      case PullPolicy.Always =>
        pullImage(image)
    }
  }

  /** Pull the image if not present. */
  def pullImageIfNotPresent(image: String): Future[Unit] = {
    dockerClient.inspectImage(image).transformWith {
      case Success(_) =>
        logger.debug(s"Image ${image} present, no pulling")
        Future.successful(())
      case Failure(e: WrappedErrorResponse) if e.code == 404 =>
        logger.debug(s"Image ${image} not present, pulling")
        pullImage(image)
      case Failure(e) =>
        Future.failed(e)
    }
  }

  /** Pull an image. */
  def pullImage(image: String): Future[Unit] = {
    for {
      response <- dockerClient.pullImage(image)
      // This is tricky, the pull is silently aborted if we do not completely consume the resposne
      _ <- response._2.runWith(Sink.ignore)
    } yield {
      logger.debug(s"Pulled image ${image}")
      ()
    }
  }

  /**
   * Wait for a container.
   * This works around TimeoutExceptions from Akka Http.
   */
  def waitContainer(container: String, timeout: FiniteDuration): Future[ContainerWaitResponse] = {
    val finalTimeout = akkaRuntime.clock.instant().plus(timeout.toSeconds, ChronoUnit.SECONDS)

    def continueWait(): Future[ContainerWaitResponse] = {
      val ct = akkaRuntime.clock.instant()
      if (ct.isAfter(finalTimeout)) {
        Future.failed(new TimeoutException(s"Timeout waiting for container ${container}"))
      }

      dockerClient.containerWait(container).recoverWith {
        case _: TimeoutException =>
          logger.debug("Container wait failed with timeout, trying again.")
          continueWait()
      }
    }

    continueWait()
  }
}

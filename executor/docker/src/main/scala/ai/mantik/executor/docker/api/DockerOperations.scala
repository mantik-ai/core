package ai.mantik.executor.docker.api

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.docker.DockerJob.ContainerDefinition
import ai.mantik.executor.docker.api.DockerClient.WrappedErrorResponse
import ai.mantik.executor.docker.api.structures.{ ContainerWaitResponse, CreateContainerRequest, CreateContainerResponse, CreateNetworkRequest, InspectContainerResponse, ListContainerRequestFilter, ListContainerResponseRow, ListNetworkRequestFilter }
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

  /** Creates a container, pulling if necessary. */
  def createContainer(containerDefinition: ContainerDefinition): Future[CreateContainerResponse] = {
    for {
      _ <- executePullPolicy(containerDefinition.pullPolicy, containerDefinition.createRequest.Image)
      response <- dockerClient.createContainer(containerDefinition.name, containerDefinition.createRequest)
    } yield response
  }

  /** Creates a container, pulling if necessary and runs it. */
  def createAndRunContainer(containerDefinition: ContainerDefinition): Future[CreateContainerResponse] = {
    for {
      createResponse <- createContainer(containerDefinition)
      _ = logger.debug(s"Created ${containerDefinition.name} (${containerDefinition.createRequest.Image})")
      _ <- dockerClient.startContainer(createResponse.Id)
      _ = logger.debug(s"Started ${containerDefinition.name} (${containerDefinition.createRequest.Image})")
    } yield createResponse
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

  /**
   * List containers.
   * @param all if true, only running containers are returned.
   */
  def listContainers(all: Boolean, labelFilters: Seq[(String, String)]): Future[Vector[ListContainerResponseRow]] = {
    dockerClient.listContainersFiltered(
      all,
      ListContainerRequestFilter.forLabelKeyValue(labelFilters: _*)
    )
  }

  /** Ensures the existance of a running container, returns the ID if it's already running.  */
  def ensureContainer(name: String, createContainerRequest: CreateContainerRequest): Future[String] = {
    checkExistance(name).flatMap {
      case Some(existing) =>
        logger.info(s"Container ${name} already exists with Id ${existing.Id}")
        Future.successful(existing.Id)
      case None =>
        (for {
          _ <- pullImageIfNotPresent(createContainerRequest.Image)
          res <- dockerClient.createContainer(name, createContainerRequest)
          _ <- dockerClient.startContainer(res.Id)
        } yield {
          res.Id
        }).andThen {
          case Success(id)  => logger.info(s"Created container ${name} with Id ${id} suceeded")
          case Failure(err) => logger.error(s"Creation of container ${name} failed", err)
        }
    }
  }

  private def checkExistance(containerName: String): Future[Option[InspectContainerResponse]] = {
    dockerClient.inspectContainer(containerName).transform {
      case Success(result)                                   => Success(Some(result))
      case Failure(e: WrappedErrorResponse) if e.code == 404 => Success(None)
    }
  }

  /** Ensures the existance of a network, returns the Id. */
  def ensureNetwork(name: String, createNetworkRequest: CreateNetworkRequest): Future[String] = {
    dockerClient.listNetworksFiltered(
      ListNetworkRequestFilter(
        name = Some(Vector(name))
      )
    ).flatMap {
        case networks if networks.nonEmpty =>
          logger.info(s"Network ${name} already exists with id ${networks.head.Id}")
          Future.successful(networks.head.Id)
        case networks if networks.isEmpty =>
          dockerClient.createNetwork(createNetworkRequest).map(_.Id).andThen {
            case Success(id)  => logger.info(s"Creating Network ${name} with ${id} succeded")
            case Failure(err) => logger.error(s"Could not ensure network ${name}", err)
          }
      }
  }
}

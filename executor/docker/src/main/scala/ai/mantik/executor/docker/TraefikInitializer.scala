package ai.mantik.executor.docker

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.docker.api.DockerClient.WrappedErrorResponse
import ai.mantik.executor.docker.api.structures.{ CreateContainerHostConfig, CreateContainerRequest, PortBindingHost, RestartPolicy }
import ai.mantik.executor.docker.api.{ DockerClient, DockerOperations }
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/** Responsible for downloading and running Traefik if not available. */
class TraefikInitializer(dockerClient: DockerClient, config: DockerExecutorConfig, operations: DockerOperations)(implicit akkaRuntime: AkkaRuntime) {
  import ai.mantik.componently.AkkaHelper._
  val logger = Logger(getClass)

  private def ingressConfig = config.ingress

  def ensureTraefikIfEnabled(): Unit = {
    if (ingressConfig.ensureTraefik) {
      ensureTraefik()
    }
  }

  private def ensureTraefik(): Unit = {
    checkExistance().flatMap { existant =>
      if (existant) {
        logger.info("Traefik already existant, no change")
        Future.successful(())
      } else {
        logger.info("Traefik not existant, trying to start")
        val result = for {
          _ <- operations.pullImageIfNotPresent(ingressConfig.traefikImage)
          _ <- runContainer()
        } yield {
          logger.info("Traefik image should run now")
          ()
        }
        result.failed.foreach { failure =>
          logger.error("Ensuring Traefik failed", failure)
        }
        result
      }
    }
  }

  def checkExistance(): Future[Boolean] = {
    dockerClient.inspectContainer(ingressConfig.traefikContainerName).transform {
      case Success(_)                                        => Success(true)
      case Failure(e: WrappedErrorResponse) if e.code == 404 => Success(false)
    }
  }

  def runContainer(): Future[Unit] = {
    val request = CreateContainerRequest(
      Image = ingressConfig.traefikImage,
      Cmd = Vector("--docker"),
      Labels = Map(
        DockerConstants.ManagedByLabelName -> DockerConstants.ManabedByLabelValue
      ),
      HostConfig = CreateContainerHostConfig(
        PortBindings = Map(
          "80/tcp" -> Vector(PortBindingHost(
            HostPort = s"${ingressConfig.traefikPort}"
          ))
        ),
        Binds = Some(
          Vector(
            "/var/run/docker.sock:/var/run/docker.sock"
          )
        ),
        RestartPolicy = Some(RestartPolicy(
          Name = "unless-stopped"
        ))
      )
    )
    for {
      res <- dockerClient.createContainer(ingressConfig.traefikContainerName, request)
      _ <- dockerClient.startContainer(res.Id)
    } yield {
      ()
    }
  }
}

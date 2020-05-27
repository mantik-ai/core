package ai.mantik.executor.docker

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.docker.api.{ DockerClient, DockerOperations }
import ai.mantik.executor.docker.api.structures.{ CreateContainerHostConfig, CreateContainerNetworkSpecificConfig, CreateContainerNetworkingConfig, CreateContainerRequest, CreateNetworkRequest, PortBindingHost, RestartPolicy }

import scala.concurrent.Future

/** Handles initalization of extra Services (Traefik etc.) */
class ExtraServices(
    executorConfig: DockerExecutorConfig,
    dockerOperations: DockerOperations
)(
    implicit
    akkaRuntime: AkkaRuntime
) extends ComponentBase {

  /** Id of the worker network. */
  val workerNetworkId: Future[String] = dockerOperations.ensureNetwork(
    executorConfig.workerNetwork,
    CreateNetworkRequest(
      Name = executorConfig.workerNetwork
    )
  )

  private val traefikStartRequest = CreateContainerRequest(
    Image = executorConfig.ingress.traefikImage,
    Cmd = Vector("--docker"),
    Labels = Map(
      DockerConstants.ManagedByLabelName -> DockerConstants.ManagedByLabelValue
    ),
    HostConfig = CreateContainerHostConfig(
      PortBindings = Map(
        "80/tcp" -> Vector(PortBindingHost(
          HostPort = s"${executorConfig.ingress.traefikPort}"
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

  /** Traefik. */
  val traefikContainerId: Future[Option[String]] = workerNetworkId.flatMap { networkId =>
    if (executorConfig.ingress.ensureTraefik) {
      val fullContainer = traefikStartRequest.withNetwork(
        executorConfig.workerNetwork, CreateContainerNetworkSpecificConfig(
          NetworkID = Some(networkId)
        )
      )
      dockerOperations.ensureContainer(executorConfig.ingress.traefikContainerName, fullContainer)
        .map(Some(_))
    } else {
      Future.successful(None)
    }
  }

  private val grpcStartRequest = CreateContainerRequest(
    Image = executorConfig.common.grpcProxy.container.image,
    Cmd = executorConfig.common.grpcProxy.container.parameters.toVector,
    Labels = Map(
      DockerConstants.ManagedByLabelName -> DockerConstants.ManagedByLabelValue
    ),
    HostConfig = CreateContainerHostConfig(
      PortBindings = Map(
        s"${executorConfig.common.grpcProxy.port}/tcp" -> Vector(PortBindingHost(
          HostPort = s"${executorConfig.common.grpcProxy.externalPort}"
        ))
      ),
      RestartPolicy = Some(RestartPolicy(
        Name = "unless-stopped"
      ))
    )
  )

  /** Id of gRpc Proxy. */
  val grpcProxy: Future[Option[String]] = workerNetworkId.flatMap { networkId =>
    if (executorConfig.common.grpcProxy.enabled) {
      val fullContainer = grpcStartRequest.withNetwork(
        executorConfig.workerNetwork, CreateContainerNetworkSpecificConfig(
          NetworkID = Some(networkId)
        )
      )
      dockerOperations.ensureContainer(executorConfig.common.grpcProxy.containerName, fullContainer).map(Some(_))
    } else {
      Future.successful(None)
    }
  }
}

package ai.mantik.executor.common

import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import com.typesafe.config.{ Config => TypesafeConfig }

/** Common settings for various executors. */
case class CommonConfig(
    sideCar: Container,
    coordinator: Container,
    payloadPreparer: Container,
    pipelineController: Container,
    dockerConfig: DockerConfig,
    disablePull: Boolean
)

object CommonConfig {

  def fromTypesafeConfig(c: TypesafeConfig): CommonConfig = {
    val root = c.getConfig("mantik.executor")
    val dockerPath = root.getConfig("docker")
    val containersConfig = root.getConfig("containers")
    val dockerConfig = DockerConfig.parseFromConfig(dockerPath)
    CommonConfig(
      sideCar = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(containersConfig.getConfig("sideCar"))),
      coordinator = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(containersConfig.getConfig("coordinator"))),
      payloadPreparer = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(containersConfig.getConfig("payloadPreparer"))),
      pipelineController = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(containersConfig.getConfig("pipelineController"))),
      dockerConfig = dockerConfig,
      disablePull = root.getBoolean("behaviour.disablePull")
    )
  }
}

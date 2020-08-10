package ai.mantik.executor.common

import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import com.typesafe.config.{ Config => TypesafeConfig }

/** Common settings for various executors. */
case class CommonConfig(
    sideCar: Container,
    coordinator: Container,
    mnpPreparer: Container,
    payloadPreparer: Container,
    mnpPipelineController: Container,
    pipelineController: Container,
    dockerConfig: DockerConfig,
    disablePull: Boolean,
    grpcProxy: GrpcProxyConfig
)

object CommonConfig {

  def fromTypesafeConfig(c: TypesafeConfig): CommonConfig = {
    val root = c.getConfig("mantik.executor")
    val dockerPath = root.getConfig("docker")
    val containersConfig = root.getConfig("containers")
    val dockerConfig = DockerConfig.parseFromConfig(dockerPath)
    def rc(name: String): Container = {
      dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(containersConfig.getConfig(name)))
    }
    CommonConfig(
      sideCar = rc("sideCar"),
      coordinator = rc("coordinator"),
      mnpPreparer = rc("mnpPreparer"),
      payloadPreparer = rc("payloadPreparer"),
      mnpPipelineController = rc("mnpPipelineController"),
      pipelineController = rc("pipelineController"),
      dockerConfig = dockerConfig,
      disablePull = root.getBoolean("behaviour.disablePull"),
      grpcProxy = GrpcProxyConfig.fromTypesafeConfig(c)
    )
  }
}

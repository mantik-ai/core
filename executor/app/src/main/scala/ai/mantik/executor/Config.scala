package ai.mantik.executor

import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import com.typesafe.config.{ ConfigFactory, Config => TypesafeConfig }

/**
 * Configuration for the execution.
 *
 * @param sideCar defines the way the side car is started
 * @param coordinator defines the way the coordinator is started
 * @param payloadPreparer defines the way the payload-Preparer is started
 * @param pipelineController defines the way the pipelineController is started
 * @param podTrackerId special id, so that the executor knows which pods to track state.
 * @param interface interface to listen on
 * @param port port to listen on
 * @param dockerConfig docker configuration
 * @param enableExistingServiceNodeCollapse if true, nodes for existing services will be collapsed.
 */
case class Config(
    sideCar: Container,
    coordinator: Container,
    payloadPreparer: Container,
    pipelineController: Container,
    podTrackerId: String,
    interface: String,
    port: Int,
    kubernetes: KubernetesConfig,
    dockerConfig: DockerConfig,
    enableExistingServiceNodeCollapse: Boolean
)

object Config {

  def apply(): Config = fromTypesafeConfig(ConfigFactory.load())

  /** Load settings from Config. */
  def fromTypesafeConfig(c: TypesafeConfig): Config = {
    val dockerConfig = DockerConfig.parseFromConfig(c.getObject("docker").toConfig)
    Config(
      sideCar = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(c.getConfig("containers.sideCar"))),
      coordinator = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(c.getConfig("containers.coordinator"))),
      payloadPreparer = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(c.getConfig("containers.payloadPreparer"))),
      pipelineController = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(c.getConfig("containers.pipelineController"))),
      podTrackerId = c.getString("app.podTrackerId"),
      kubernetes = KubernetesConfig.fromTypesafe(c.getConfig("kubernetes")),
      interface = c.getString("app.server.interface"),
      port = c.getInt("app.server.port"),
      dockerConfig = dockerConfig,
      enableExistingServiceNodeCollapse = c.getBoolean("app.enableExistingServiceNodeCollapse")
    )
  }
}
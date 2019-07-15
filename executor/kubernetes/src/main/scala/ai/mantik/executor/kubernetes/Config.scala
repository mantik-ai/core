package ai.mantik.executor.kubernetes

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
 * @param dockerConfig docker configuration
 * @param enableExistingServiceNodeCollapse if true, nodes for existing services will be collapsed.
 */
case class Config(
    sideCar: Container,
    coordinator: Container,
    payloadPreparer: Container,
    pipelineController: Container,
    podTrackerId: String,
    kubernetes: KubernetesConfig,
    dockerConfig: DockerConfig,
    enableExistingServiceNodeCollapse: Boolean
)

object Config {

  /** Load settings from Config. */
  def fromTypesafeConfig(c: TypesafeConfig): Config = {
    val root = c.getConfig("mantik.executor")
    val dockerConfig = DockerConfig.parseFromConfig(root.getObject("docker").toConfig)
    Config(
      sideCar = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(root.getConfig("containers.sideCar"))),
      coordinator = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(root.getConfig("containers.coordinator"))),
      payloadPreparer = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(root.getConfig("containers.payloadPreparer"))),
      pipelineController = dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(root.getConfig("containers.pipelineController"))),
      podTrackerId = root.getString("behaviour.podTrackerId"),
      kubernetes = KubernetesConfig.fromTypesafe(root.getConfig("kubernetes")),
      dockerConfig = dockerConfig,
      enableExistingServiceNodeCollapse = root.getBoolean("behaviour.enableExistingServiceNodeCollapse")
    )
  }
}
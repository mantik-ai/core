package ai.mantik.executor.kubernetes

import ai.mantik.executor.common.CommonConfig
import ai.mantik.executor.model.docker.DockerConfig
import com.typesafe.config.{ Config => TypesafeConfig }

/**
 * Configuration for the execution.
 *
 * @param common common executor settings.
 * @param podTrackerId special id, so that the executor knows which pods to track state.
 * @param enableExistingServiceNodeCollapse if true, nodes for existing services will be collapsed.
 */
case class Config(
    common: CommonConfig,
    podTrackerId: String,
    kubernetes: KubernetesConfig,
    enableExistingServiceNodeCollapse: Boolean
) {
  def dockerConfig: DockerConfig = common.dockerConfig
}

object Config {

  /** Load settings from Config. */
  def fromTypesafeConfig(c: TypesafeConfig): Config = {
    val root = c.getConfig("mantik.executor")
    val commonConfig = CommonConfig.fromTypesafeConfig(c)
    Config(
      common = commonConfig,
      podTrackerId = root.getString("behaviour.podTrackerId"),
      kubernetes = KubernetesConfig.fromTypesafe(root.getConfig("kubernetes")),
      enableExistingServiceNodeCollapse = root.getBoolean("behaviour.enableExistingServiceNodeCollapse")
    )
  }
}
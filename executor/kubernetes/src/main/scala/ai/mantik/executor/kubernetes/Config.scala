package ai.mantik.executor.kubernetes

import ai.mantik.executor.common.CommonConfig
import ai.mantik.executor.model.docker.DockerConfig
import com.typesafe.config.{Config => TypesafeConfig}

/**
  * Configuration for the execution.
  *
  * @param common common executor settings.
  * @param kubernetes kubernetes specific settings
  */
case class Config(
    common: CommonConfig,
    kubernetes: KubernetesConfig
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
      kubernetes = KubernetesConfig.fromTypesafe(root.getConfig("kubernetes"))
    )
  }
}

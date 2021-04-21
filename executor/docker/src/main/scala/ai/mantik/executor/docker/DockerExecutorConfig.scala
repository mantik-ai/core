package ai.mantik.executor.docker

import ai.mantik.executor.common.CommonConfig
import com.typesafe.config.Config

/** Configuration for Docker Executor. */
case class DockerExecutorConfig(
    common: CommonConfig,
    ingress: IngressConfig,
    workerNetwork: String
)

object DockerExecutorConfig {
  def fromTypesafeConfig(config: Config): DockerExecutorConfig = {
    DockerExecutorConfig(
      common = CommonConfig.fromTypesafeConfig(config),
      ingress = IngressConfig.fromTypesafeConfig(config),
      workerNetwork = config.getString("mantik.executor.docker.workerNetwork")
    )
  }
}

case class IngressConfig(
    ensureTraefik: Boolean,
    traefikImage: String,
    traefikContainerName: String,
    traefikPort: Int,
    labels: Map[String, String],
    remoteUrl: String
)

object IngressConfig {
  def fromTypesafeConfig(config: Config): IngressConfig = {
    import ai.mantik.componently.utils.ConfigExtensions._
    val path = "mantik.executor.docker.ingress"
    val subConfig = config.getConfig(path)
    IngressConfig(
      ensureTraefik = subConfig.getBoolean("ensureTraefik"),
      traefikImage = subConfig.getString("traefikImage"),
      traefikContainerName = subConfig.getString("traefikContainerName"),
      traefikPort = subConfig.getInt("traefikPort"),
      labels = subConfig.getKeyValueMap("labels"),
      remoteUrl = subConfig.getString("remoteUrl")
    )
  }
}

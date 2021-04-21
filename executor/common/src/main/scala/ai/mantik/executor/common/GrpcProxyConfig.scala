package ai.mantik.executor.common

import ai.mantik.executor.model.docker.Container
import com.typesafe.config.Config

case class GrpcProxyConfig(
    enabled: Boolean,
    containerName: String,
    container: Container,
    port: Int,
    externalPort: Int
)

object GrpcProxyConfig {
  def fromTypesafeConfig(config: Config): GrpcProxyConfig = {
    val path = "mantik.executor.grpcProxy"
    val subConfig = config.getConfig(path)
    GrpcProxyConfig(
      enabled = subConfig.getBoolean("enabled"),
      containerName = subConfig.getString("containerName"),
      port = subConfig.getInt("port"),
      externalPort = subConfig.getInt("externalPort"),
      container = Container.parseFromTypesafeConfig(subConfig)
    )
  }
}

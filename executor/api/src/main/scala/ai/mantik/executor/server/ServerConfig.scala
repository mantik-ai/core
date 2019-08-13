package ai.mantik.executor.server

import com.typesafe.config.Config

/** Configuration for [[ExecutorServer]]. */
case class ServerConfig(
    interface: String,
    port: Int
)

object ServerConfig {
  /** Path for Server Config. */
  val Path = "mantik.executor.server"

  /** Parse Server Config from Typesafe config. */
  def fromTypesafe(config: Config): ServerConfig = {
    val subConfig = config.getConfig(Path)
    ServerConfig(
      interface = subConfig.getString("interface"),
      port = subConfig.getInt("port")
    )
  }
}

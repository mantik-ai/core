package ai.mantik.componently.utils

import com.typesafe.config.Config

import scala.concurrent.duration.{ Duration, FiniteDuration }

/** Extensions for [[Config]]  */
object ConfigExtensions {

  class ConfigExt(config: Config) {

    /** Return an optional String, whose config key may be missing. */
    def getOptionalString(key: String): Option[String] = {
      if (config.hasPath(key)) {
        Some(config.getString(key))
      } else {
        None
      }
    }

    /** Returns a finite duration. */
    def getFiniteDuration(key: String): FiniteDuration = {
      Duration.fromNanos(config.getDuration(key).toNanos)
    }
  }

  import scala.language.implicitConversions
  implicit def toConfigExt(config: Config): ConfigExt = new ConfigExt(config)
}

package ai.mantik.componently.utils

import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.collection.JavaConverters._

/** Extensions for [[Config]] */
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

    /** Read a key/value map from a list of objects of key and value elements. */
    def getKeyValueMap(key: String): Map[String, String] = {
      val array = config.getObjectList(key).asScala
      array.map { sub =>
        val asConfig = sub.toConfig
        asConfig.getString("key") -> asConfig.getString("value")
      }.toMap
    }
  }

  import scala.language.implicitConversions
  implicit def toConfigExt(config: Config): ConfigExt = new ConfigExt(config)
}

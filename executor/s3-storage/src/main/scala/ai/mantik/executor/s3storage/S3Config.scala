package ai.mantik.executor.s3storage

import ai.mantik.componently.utils.SecretReader
import com.typesafe.config.{ Config, ConfigException }

import scala.collection.JavaConverters._

case class S3Config(
    endpoint: String,
    region: String,
    bucket: String,
    accessId: String,
    secretId: SecretReader,
    aclWorkaround: Boolean,
    tags: Map[String, String]
) {
  def withTestTag(value: String = "integration"): S3Config = {
    copy(
      tags = tags + ((S3Config.TestTag) -> value)
    )
  }

  def hasTestTeg: Boolean = tags.contains(S3Config.TestTag)
}

object S3Config {
  /**
   * Name of tag used to mark S3 as being in used for tests.
   * (Activates features like deleting everything)
   */
  val TestTag: String = "test"
  def fromTypesafe(config: Config): S3Config = {
    val root = config.getConfig("mantik.executor.s3Storage")
    S3Config(
      endpoint = root.getString("endpoint"),
      region = root.getString("region"),
      bucket = root.getString("bucket"),
      accessId = root.getString("accessKeyId"),
      secretId = new SecretReader("secretKey", root),
      aclWorkaround = root.getBoolean("aclWorkaround"),
      tags = {
        val o = root.getObject("tags")
        o.unwrapped().asScala.map {
          case (key, s: String) => key -> s
          case (_, _)           => throw new ConfigException.WrongType(o.origin(), s"Expected string to string map for S3 Tags")
        }.toMap
      }
    )
  }
}

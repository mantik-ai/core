package ai.mantik.componently.utils

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

import com.typesafe.config.{ Config, ConfigException }
import com.typesafe.scalalogging.Logger

/**
 * Reads secrets from Typesafe Config, allowing multiple ways to read them from external values.
 * Following methods are allowed:
 *
 * `plain:plain-password` => Read the secret as string
 * `file:file` => Read the secret from a file (for kubernetes), UTF8
 * `env:variable` => Read the secret from environment variable.
 *
 * If nothing matches, it will return the plain value.
 */
final class SecretReader(configKey: String, config: Config) {

  private val logger = Logger(getClass)

  /**
   * Read the secret value.
   * @throws ConfigException if the config key was not found
   * @throws UnresolvedSecretException if the config value could not be resolved.
   */
  def read(): String = {
    val value = config.getString(configKey)
    SecretReader.readSecretFromString(value, configKey) match {
      case Some(ok) => ok
      case None =>
        logger.info(s"Secret stored in ${configKey} is not using regular prefix")
        value
    }
  }
}

object SecretReader {

  val PlainPrefix = "plain:"
  val FilePrefix = "file:"
  val EnvPrefix = "env:"

  /**
   * Read the secret value.
   * @throws UnresolvedSecretException if the config value could not be resolved.
   *
   * @return None if the string is not encoded using env/plain/file notation.
   */
  def readSecretFromString(value: String, configKey: String): Option[String] = {
    value match {
      case s if s.startsWith(PlainPrefix) =>
        Some(value.stripPrefix(PlainPrefix))
      case s if s.startsWith(FilePrefix) =>
        val path = Paths.get(value.stripPrefix(FilePrefix))
        if (!Files.isRegularFile(path)) {
          // By default symlinks are followed, which is ok.
          throw new UnresolvedSecretException(s"File ${path} is not a regular file, configKey = ${configKey}")
        }
        val content = Files.readAllBytes(path)
        Some(new String(content, StandardCharsets.UTF_8))
      case s if s.startsWith(EnvPrefix) =>
        val envName = value.stripPrefix(EnvPrefix)
        val content = System.getenv(envName)
        if (content == null) {
          throw new UnresolvedSecretException(s"Environment variable ${envName} could not be found, configKey = ${configKey}")
        }
        Some(content)
      case _ => None
    }
  }
}

/** Some secret could not be resolved. */
class UnresolvedSecretException(msg: String) extends RuntimeException
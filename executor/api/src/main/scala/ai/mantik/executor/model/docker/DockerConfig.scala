package ai.mantik.executor.model.docker

import com.typesafe.config.ConfigException.WrongType
import com.typesafe.config.{ Config, ConfigException, ConfigObject }
import io.circe.generic.JsonCodec

/** Common configuration for interacting with docker images. */
@JsonCodec
case class DockerConfig(
    defaultImageTag: Option[String] = None,
    defaultImageRepository: Option[String] = None,
    logins: Seq[DockerLogin] = Nil
) {

  /** Resolves a container (adds default image tag and repository, if not given). */
  def resolveContainer(container: Container): Container = {
    container.copy(
      image = resolveImageName(container.image)
    )
  }

  /** Resolves an image (adds default image tag and repository if not given). */
  def resolveImageName(imageName: String): String = {
    // Tricky: repo may contain ":"
    val repoDelimiterIdx = imageName.indexOf("/")
    val tagDelimiterIdx = imageName.indexOf(":", repoDelimiterIdx + 1)

    val imageWithRepository = (imageName, defaultImageRepository) match {
      case (i, _) if repoDelimiterIdx >= 0 => i
      case (i, Some(repo))                 => repo + "/" + i
      case (i, _)                          => i
    }

    val imageWithTag = (imageWithRepository, defaultImageTag) match {
      case (i, _) if tagDelimiterIdx >= 0 => i
      case (i, Some(tag))                 => i + ":" + tag
      case (i, _)                         => i
    }

    imageWithTag
  }

}

object DockerConfig {

  /** Parses docker configuration from typesafe config. */
  @throws[ConfigException]
  def parseFromConfig(config: Config): DockerConfig = {
    import scala.collection.JavaConverters._

    def optionalString(key: String): Option[String] = {
      if (config.hasPath(key)) {
        // Empty value is interpreted as None
        val value = config.getString(key)
        Some(value.trim).filter(_.nonEmpty)
      } else {
        None
      }
    }
    val logins: Seq[DockerLogin] = if (config.hasPath("logins")) {
      config.getList("logins").asScala.map {
        case c: ConfigObject => DockerLogin.parseFromConfig(c.toConfig)
        case c               => throw new WrongType(c.origin(), "Expected object")
      }
    } else {
      Nil
    }

    DockerConfig(
      defaultImageRepository = optionalString("defaultImageRepository"),
      defaultImageTag = optionalString("defaultImageTag"),
      logins = logins
    )
  }
}

@JsonCodec
case class DockerLogin(
    repository: String,
    username: String,
    password: String
)

object DockerLogin {
  def parseFromConfig(config: Config): DockerLogin = {
    DockerLogin(
      repository = config.getString("repository"),
      username = config.getString("username"),
      password = config.getString("password")
    )
  }
}
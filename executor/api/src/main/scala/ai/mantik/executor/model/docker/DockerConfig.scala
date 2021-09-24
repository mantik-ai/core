/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.executor.model.docker

import ai.mantik.componently.utils.SecretReader
import com.typesafe.config.ConfigException.WrongType
import com.typesafe.config.{Config, ConfigException, ConfigObject}
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
    import scala.jdk.CollectionConverters._

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
      config.getList("logins").asScala.toSeq.map {
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
      password = {
        val value = config.getString("password")
        SecretReader.readSecretFromString(value, "password").getOrElse(value)
      }
    )
  }
}

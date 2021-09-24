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
package ai.mantik.executor.s3storage

import ai.mantik.componently.utils.SecretReader
import com.typesafe.config.{Config, ConfigException}

import scala.jdk.CollectionConverters._

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
        o.unwrapped()
          .asScala
          .map {
            case (key, s: String) => key -> s
            case (_, _)           => throw new ConfigException.WrongType(o.origin(), s"Expected string to string map for S3 Tags")
          }
          .toMap
      }
    )
  }
}

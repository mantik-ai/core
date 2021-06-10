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
package ai.mantik.componently.utils

import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.collection.JavaConverters._

/** Extensions for [[com.typesafe.config.Config]] */
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

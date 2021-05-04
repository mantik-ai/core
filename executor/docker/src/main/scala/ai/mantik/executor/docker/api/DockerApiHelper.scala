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
package ai.mantik.executor.docker.api

import java.util.Locale

import scala.util.Try

object DockerApiHelper {

  private val statusCodeExtractRegex = "^exited \\((\\d+)\\).*".r

  /**
    * Try to decode the status code from human readable status field.
    * This can be useful, if we want to know if a container failed
    * from reading the container listing without further inspection.
    */
  def decodeStatusCodeFromStatus(status: String): Option[Int] = {
    val lc = status.toLowerCase(Locale.US)
    lc match {
      case statusCodeExtractRegex(value) =>
        Try(value.toInt).toOption
      case _ => None
    }
  }
}

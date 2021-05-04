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
package ai.mantik.testutils

import java.io.{File, FileNotFoundException}
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import org.apache.commons.io.IOUtils

/** Helpers for dealing with (test) Resources */
trait ResourceSupport {
  self: TestBase =>

  /** Returns the resource content as string. */
  def resourceAsString(name: String): String = {
    IOUtils.resourceToString(name, StandardCharsets.UTF_8)
  }

  /** Returns the resource path (note: this doesn't work if the resource is inside a JAR). */
  def resourcePath(name: String): Path = {
    val res = getClass.getResource(name)
    if (res == null) {
      throw new FileNotFoundException(s"Resource ${name} not found")
    }
    new File(res.toURI).toPath
  }
}

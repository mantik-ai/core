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
package ai.mantik.bridge.scalafn.cs

import java.net.URL

/** Figures out the byte code of a class. */
class ClassByteCodeLoader(loader: ClassLoader = Thread.currentThread().getContextClassLoader) {

  /** Returns class file name and URL to content. */
  def classByteCodeFromName(name: String): (String, URL) = {
    val encoded = name.replace('.', '/')
    val classFileName = encoded + ".class"
    val url = Option(loader.getResource(classFileName)).getOrElse {
      throw new ClosureSerializerException(s"Could not find class file for ${name}, guessed ${classFileName}")
    }
    (classFileName, url)
  }

  def rawClassByteCodeFromName(name: String): Array[Byte] = {
    readBytes(classByteCodeFromName(name)._2)
  }

  private def readBytes(url: URL): Array[Byte] = {
    val stream = url.openStream()
    try {
      stream.readAllBytes()
    } finally {
      stream.close()
    }
  }
}

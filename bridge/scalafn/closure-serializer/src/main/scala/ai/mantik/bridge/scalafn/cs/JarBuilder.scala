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

import java.io.{FileOutputStream, InputStream}
import java.nio.file.{Files, Path}
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/** Helper for building Jars from classes. */
private[cs] class JarBuilder(byteCodeLoader: ClassByteCodeLoader) {

  /**
    * Build a JAR containg given classes
    * @param classes classes to add to the JAR
    * @param extraFiles extra files to add to the JAR
    * @return a temporary file containing the JAR.
    */
  def buildJar(
      classes: Seq[String],
      extraFiles: Seq[(String, InputStream)] = Nil
  ): Path = {
    val name = Files.createTempFile("mantik-cs", ".jar")
    val fileOut = new FileOutputStream(name.toFile)
    try {
      val jarOutputStream = new JarOutputStream(fileOut)
      classes.foreach { clazz =>
        val (name, url) = byteCodeLoader.classByteCodeFromName(clazz)
        val zipEntry = new ZipEntry(name)
        jarOutputStream.putNextEntry(zipEntry)
        val source = url.openStream()
        try {
          source.transferTo(jarOutputStream)
        } finally {
          source.close()
        }
        jarOutputStream.closeEntry()
      }
      extraFiles.foreach { case (name, source) =>
        val zipEntry = new ZipEntry(name)
        jarOutputStream.putNextEntry(zipEntry)
        try {
          source.transferTo(jarOutputStream)
        } finally {
          source.close()
        }
      }
      jarOutputStream.close()
    } finally {
      fileOut.close()
    }
    name
  }
}

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
package ai.mantik.ds.helper

import java.io._
import java.nio.file.Path
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import _root_.akka.stream._
import _root_.akka.stream.scaladsl._
import _root_.akka.util.ByteString

/**
  * Utilities for the Zip Format.
  * Some info: https://www.baeldung.com/java-compress-and-uncompress
  */
object ZipUtils {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Zip a directory as asynchronous source. */
  def zipDirectory(directory: Path, timeout: FiniteDuration)(
      implicit ec: ExecutionContext
  ): Source[ByteString, Future[Unit]] = {
    val outputStream = StreamConverters.asOutputStream(timeout)
    outputStream.mapMaterializedValue { outputStream =>
      Future {
        zipDirectory(directory, outputStream)
      }
    }
  }

  /** Zip a directory into a file. */
  def zipDirectory(directory: Path, zipFile: Path): Unit = {
    val fileOutputStream = new FileOutputStream(zipFile.toFile)
    try {
      zipDirectory(directory, fileOutputStream)
    } finally {
      fileOutputStream.close()
    }
  }

  /**
    * Zip a byte stream in memory into a ZIP-File containing one file.
    * The materialized value will release as soon as the zipping is done.
    * Note: the error handling is not very stable yet.
    */
  def zipSingleFileStream(
      fileName: String = "file1"
  )(implicit ec: ExecutionContext): Flow[ByteString, ByteString, Future[Unit]] = {
    val output = StreamConverters.asOutputStream()
    val input = StreamConverters.asInputStream()

    output.async

    val myTestCase = new RuntimeException()

    val result = Flow.fromSinkAndSourceMat(input, output) { case (inputStream, outputStream) =>
      Future {
        try {
          val zipOutputStream = new ZipOutputStream(outputStream)
          zipOutputStream.putNextEntry(new ZipEntry(fileName))
          IOUtils.copy(inputStream, zipOutputStream)
          zipOutputStream.finish()
          outputStream.flush()
          zipOutputStream.close()
        } catch {
          case e: IOException if e.getMessage.contains("Reactive stream is terminated") =>
            // This happens if the down stream is closed too early
            logger.info(s"Destination closed", e)
          case e: Exception =>
            logger.warn(s"Error during zipping", e)
            throw e
        } finally {
          outputStream.close()
        }
      }
    }
    result
  }

  /**
    * Unzip the first found file in a zip file, the materialize value will release as soon as the unzipping is done.
    * Note: the error handling is not very stable yet.
    */
  def unzipSingleFileStream()(implicit ec: ExecutionContext): Flow[ByteString, ByteString, Future[Unit]] = {
    val output = StreamConverters.asOutputStream()
    val input = StreamConverters.asInputStream()
    val result = Flow.fromSinkAndSourceMat(input, output) { case (inputStream, outputStream) =>
      Future {
        try {
          val zipInputStream = new ZipInputStream(inputStream)
          var nextEntry = zipInputStream.getNextEntry
          while (nextEntry != null && nextEntry.isDirectory) {
            nextEntry = zipInputStream.getNextEntry
          }
          if (nextEntry == null) {
            outputStream.close()
            throw new IllegalStateException(s"No file found in zip archive")
          }
          IOUtils.copy(zipInputStream, outputStream)
          // Also consume the rest of it
          nextEntry = zipInputStream.getNextEntry
          while (nextEntry != null) {
            logger.info(s"Ignoring ${nextEntry.getName}")
            nextEntry = zipInputStream.getNextEntry
          }
          outputStream.flush()
          outputStream.close()
          ()
        } catch {
          case e: IOException if e.getMessage.contains("Reactive stream is terminated") =>
            // Seems harmless in multiple tests.
            logger.debug(s"Destination closed", e)
          case e: Exception =>
            logger.warn(s"Error during on-the-fly unzipping", e)
            throw e
        } finally {
          outputStream.close()
        }

      }
    }
    result
  }

  /**
    * Zip a whole directory.
    * Note: mainly for testing, usually uploading and compression is done via Mantik CLI Tool.
    */
  private def zipDirectory(directory: Path, outputStream: OutputStream): Unit = {
    val directoryFile = directory.toFile
    if (!directoryFile.isDirectory) {
      throw new IllegalArgumentException(s"${directory} is not a directory")
    }
    val zipOutputStream = new ZipOutputStream(outputStream)

    def addFile(file: File, name: String): Unit = {
      if (file.isHidden) {
        logger.debug(s"Ignoring ${file}, it's hidden")
        return
      }
      if (file.isDirectory) {
        val nameToAdd = if (name.endsWith("/")) name else name + "/"
        zipOutputStream.putNextEntry(new ZipEntry(nameToAdd))
        file.listFiles().foreach { subFile =>
          addFile(subFile, name + "/" + subFile.getName)
        }
      } else {
        zipOutputStream.putNextEntry(new ZipEntry(name))
        val inputStream = new FileInputStream(file)
        try {
          IOUtils.copy(inputStream, zipOutputStream)
        } finally {
          inputStream.close()
        }
      }
    }

    try {
      directoryFile.listFiles().foreach { file =>
        addFile(file, file.getName)
      }
    } finally {
      zipOutputStream.close()
    }
  }

  /** Unzip a Zip-File-Stream into a target directory (non-blocking) */
  def unzip(input: Source[ByteString, _], targetDirectory: Path, readTimeout: FiniteDuration)(
      implicit materializer: Materializer
  ): Future[Unit] = {
    val converter = StreamConverters.asInputStream(readTimeout)
    val inputStream = input.runWith(converter)
    implicit val ec = materializer.executionContext
    Future {
      unzip(inputStream, targetDirectory)
    }
  }

  /** Unzip a single file into target directory. */
  def unzip(file: Path, targetDirectory: Path): Unit = {
    val fileInputStream = new FileInputStream(file.toFile)
    unzip(fileInputStream, targetDirectory)
  }

  /** Unzip a Zip File input stream to a target directory. */
  private def unzip(inputStream: InputStream, targetDirectory: Path): Unit = {
    val targetDirectoryFile = targetDirectory.toFile
    if (!targetDirectoryFile.isDirectory) {
      if (Option(targetDirectoryFile.getParentFile).exists(_.isDirectory) && targetDirectoryFile.mkdir()) {
        // ok, directory created
      } else {
        throw new IOException(s"Target directory does not exit, or could not be created")
      }
    }
    val zipInputStream = new ZipInputStream(inputStream)
    var entry: ZipEntry = null
    while ({
      entry = zipInputStream.getNextEntry
      entry != null
    }) {
      unzipSingleEntry(zipInputStream, entry, targetDirectory)
    }
  }

  private def unzipSingleEntry(zipInputStream: ZipInputStream, zipEntry: ZipEntry, targetDirectory: Path): Unit = {
    val targetPath = targetDirectory.resolve(zipEntry.getName).normalize()
    if (!targetPath.startsWith(targetDirectory)) {
      logger.warn(s"Skipping ${targetPath}, as it's not inside target directory ${targetDirectory}")
    } else {
      val targetFile = targetPath.toFile
      if (zipEntry.isDirectory) {
        targetFile.mkdirs()
      } else {
        // Create parent directories
        Option(targetFile.getParentFile).foreach(_.mkdir())
        val targetOutputStream = new FileOutputStream(targetFile)
        try {
          IOUtils.copy(zipInputStream, targetOutputStream)
        } finally {
          targetOutputStream.close()
        }
      }
    }
  }
}

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

import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterEach

/** Provides a temporary directory. */
trait TempDirSupport extends BeforeAndAfterEach {
  self: TestBase =>

  private var tempDir: Option[Path] = None

  protected def tempDirectory: Path = tempDir.getOrElse {
    throw new IllegalStateException(s"Can only call temp dir inside a testcase")
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    tempDir = Some(Files.createTempDirectory(s"test_${getClass.getSimpleName.toLowerCase}"))
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    tempDir.foreach { dir =>
      FileUtils.deleteDirectory(dir.toFile)
      tempDir = None
    }
  }

  /**
    * Compares equalaity of two directories.
    *
    * Source: https://stackoverflow.com/questions/14522239/test-two-directory-trees-for-equality
    * Modified for scala test
    * @param one first directory
    * @param other second directory
    */
  def verifyDirsAreEqual(one: Path, other: Path): Unit = {
    import java.nio.file.{FileVisitResult, Files, SimpleFileVisitor}
    import java.nio.file.attribute.BasicFileAttributes
    import java.util

    Files.walkFileTree(
      one,
      new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val result = super.visitFile(file, attrs)
          // get the relative file name from path "one"
          val relativize = one.relativize(file)
          // construct the path for the counterpart file in "other"
          val fileInOther = other.resolve(relativize)
          val otherBytes = Files.readAllBytes(fileInOther)
          val thisBytes = Files.readAllBytes(file)
          if (!util.Arrays.equals(otherBytes, thisBytes))
            fail(file.toString + " is not equal to " + fileInOther.toString)
          logger.info(s"Compared ${file} with ${fileInOther}")
          result
        }
      }
    )
  }
}

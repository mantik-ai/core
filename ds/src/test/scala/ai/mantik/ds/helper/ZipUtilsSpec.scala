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

import ai.mantik.ds.testutil.{GlobalAkkaSupport, TempDirSupport, TestBase}
import org.apache.commons.io.FileUtils
import _root_.akka.stream.scaladsl._
import _root_.akka.util.ByteString
import ai.mantik.testutils.ResourceSupport

import scala.concurrent.duration._

class ZipUtilsSpec extends TestBase with TempDirSupport with GlobalAkkaSupport with ResourceSupport {
  val sampleDirectory = resourcePath("/sample_directory")
  val sampleFile = resourcePath("/sample_directory/numbers.png")

  "zipping and unzipping" should "work" in {
    val zipFile = tempDirectory.resolve("test.zip")
    val unzipped = tempDirectory.resolve("unzip")
    ZipUtils.zipDirectory(sampleDirectory, zipFile)

    ZipUtils.unzip(zipFile, unzipped)

    verifyDirsAreEqual(sampleDirectory, unzipped)
  }

  it should "work using the async apis" in {
    val unzipped = tempDirectory.resolve("unzip")
    val source = ZipUtils.zipDirectory(sampleDirectory, 5.seconds)
    await(ZipUtils.unzip(source, unzipped, 5.seconds))
    verifyDirsAreEqual(sampleDirectory, unzipped)
  }

  it should "zip via via single flow" in {
    val sampleFileSource = FileIO.fromPath(sampleFile)
    val targetFile = tempDirectory.resolve("test.zip")
    val sampleFileDestination = FileIO.toPath(targetFile)
    val result =
      await(sampleFileSource.via(ZipUtils.zipSingleFileStream("file1")).toMat(sampleFileDestination)(Keep.right).run())
    targetFile.toFile.exists() shouldBe true

    val destinationUnzip = tempDirectory.resolve("unzip")
    ZipUtils.unzip(targetFile, destinationUnzip)

    FileUtils.readFileToByteArray(sampleFile.toFile) shouldBe
      FileUtils.readFileToByteArray(destinationUnzip.resolve("file1").toFile)
  }

  it should "unzip via single flow" in {
    // zipping like above
    val targetFile = tempDirectory.resolve("test.zip")
    await(
      FileIO.fromPath(sampleFile).via(ZipUtils.zipSingleFileStream()).toMat(FileIO.toPath(targetFile))(Keep.right).run()
    )

    val unzipTargetFile = tempDirectory.resolve("unzipped")
    await(
      FileIO
        .fromPath(targetFile)
        .via(ZipUtils.unzipSingleFileStream())
        .toMat(FileIO.toPath(unzipTargetFile))(Keep.right)
        .run()
    )

    FileUtils.readFileToByteArray(sampleFile.toFile) shouldBe
      FileUtils.readFileToByteArray(unzipTargetFile.toFile)
  }

  it should "zip transparently" in {
    val result = FileIO
      .fromPath(sampleFile)
      .via(ZipUtils.zipSingleFileStream())
      .via(ZipUtils.unzipSingleFileStream())
      .runWith(Sink.seq)
    val collected = await(result)

    val putTogether = collected.fold(ByteString.empty)(_ ++ _)
    val original = FileUtils.readFileToByteArray(sampleFile.toFile)
    putTogether shouldBe original
  }
}

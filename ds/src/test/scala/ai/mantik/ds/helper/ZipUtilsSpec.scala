package ai.mantik.ds.helper

import java.io.File

import ai.mantik.ds.testutil.{ GlobalAkkaSupport, TempDirSupport, TestBase }
import org.apache.commons.io.FileUtils
import _root_.akka.stream.scaladsl._
import _root_.akka.util.ByteString

import scala.concurrent.duration._

class ZipUtilsSpec extends TestBase with TempDirSupport with GlobalAkkaSupport {
  val sampleDirectory = new File(getClass.getResource("/sample_directory").toURI).toPath
  val sampleFile = new File(getClass.getResource("/sample_directory/numbers.png").toURI).toPath

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
    val result = await(sampleFileSource.via(ZipUtils.zipSingleFileStream("file1")).toMat(sampleFileDestination)(Keep.right).run())
    targetFile.toFile.exists() shouldBe true

    val destinationUnzip = tempDirectory.resolve("unzip")
    ZipUtils.unzip(targetFile, destinationUnzip)

    FileUtils.readFileToByteArray(sampleFile.toFile) shouldBe
      FileUtils.readFileToByteArray(destinationUnzip.resolve("file1").toFile)
  }

  it should "unzip via single flow" in {
    // zipping like above
    val targetFile = tempDirectory.resolve("test.zip")
    await(FileIO.fromPath(sampleFile).via(ZipUtils.zipSingleFileStream()).toMat(FileIO.toPath(targetFile))(Keep.right).run())

    val unzipTargetFile = tempDirectory.resolve("unzipped")
    await(FileIO.fromPath(targetFile).via(ZipUtils.unzipSingleFileStream()).toMat(FileIO.toPath(unzipTargetFile))(Keep.right).run())

    FileUtils.readFileToByteArray(sampleFile.toFile) shouldBe
      FileUtils.readFileToByteArray(unzipTargetFile.toFile)
  }

  it should "zip transparently" in {
    val result = FileIO.fromPath(sampleFile)
      .via(ZipUtils.zipSingleFileStream())
      .via(ZipUtils.unzipSingleFileStream())
      .runWith(Sink.seq)
    val collected = await(result)

    val putTogether = collected.fold(ByteString.empty)(_ ++ _)
    val original = FileUtils.readFileToByteArray(sampleFile.toFile)
    putTogether shouldBe original
  }
}

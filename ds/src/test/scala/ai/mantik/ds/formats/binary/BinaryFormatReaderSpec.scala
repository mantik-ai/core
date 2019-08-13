package ai.mantik.ds.formats.binary

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, NoSuchFileException }

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.{ Primitive, RootElement, TabularRow }
import ai.mantik.ds.testutil.{ GlobalAkkaSupport, TempDirSupport, TestBase }
import org.apache.commons.io.{ FileUtils, IOUtils }
import io.circe.yaml.{ parser => YamlParser }

class BinaryFormatReaderSpec extends TestBase with GlobalAkkaSupport with TempDirSupport {

  trait Env {

    def executeReading(description: BinaryDataSetDescription): Seq[RootElement] = {
      val reader = new BinaryFormatReader(description, tempDirectory)
      val source = reader.read()

      val result = collectSource(source)
      result
    }
  }

  trait EnvWithSimpleFile extends Env {
    val file = new File(tempDirectory.toFile, "1.binary")
    FileUtils.writeByteArrayToFile(
      file, Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    )

    val file2 = new File(tempDirectory.toFile, "2.binary")
    FileUtils.writeByteArrayToFile(
      file2, Array[Byte](15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )
  }

  it should "decode a single trivial element" in new EnvWithSimpleFile {
    val layout = BinaryDataSetDescription(
      `type` = TabularData(
        "x" -> FundamentalType.Int8
      ),
      files = Seq(
        BinaryFileDescription(
          file.getName,
          content = Seq(
            BinaryFileContent.Element("x")
          )
        )
      )
    )
    val result = executeReading(layout)

    val expectedRows = for (i <- 0 until 16) yield {
      TabularRow(Primitive[Byte](i.toByte))
    }

    result shouldBe expectedRows
  }

  it should "support initial steps" in new EnvWithSimpleFile {
    val layout = BinaryDataSetDescription(
      `type` = TabularData(
        "x" -> FundamentalType.Int8
      ),
      files = Seq(
        BinaryFileDescription(
          file.getName,
          skip = Some(4),
          content = Seq(
            BinaryFileContent.Element("x")
          )
        )
      )
    )
    val result = executeReading(layout)

    val expectedRows = for (i <- 4 until 16) yield {
      TabularRow(Primitive[Byte](i.toByte))
    }

    result shouldBe expectedRows
  }

  it should "support byte skipping" in new EnvWithSimpleFile {
    val layout = BinaryDataSetDescription(
      `type` = TabularData(
        "x" -> FundamentalType.Int8
      ),
      files = Seq(
        BinaryFileDescription(
          file.getName,
          content = Seq(
            BinaryFileContent.Skip(1),
            BinaryFileContent.Element("x"),
            BinaryFileContent.Skip(2)
          )
        )
      )
    )
    val result = executeReading(layout)

    val expectedRows = Seq(
      TabularRow(Primitive[Byte](1)),
      TabularRow(Primitive[Byte](5)),
      TabularRow(Primitive[Byte](9)),
      TabularRow(Primitive[Byte](13))
    )

    result shouldBe expectedRows
  }

  it should "support strides" in new EnvWithSimpleFile {
    val layout = BinaryDataSetDescription(
      `type` = TabularData(
        "x" -> FundamentalType.Int8
      ),
      files = Seq(
        BinaryFileDescription(
          file.getName,
          content = Seq(
            BinaryFileContent.Stride(8),
            BinaryFileContent.Element("x")
          )
        )
      )
    )
    val result = executeReading(layout)

    val expectedRows = Seq(
      TabularRow(Primitive[Byte](0)),
      TabularRow(Primitive[Byte](8))
    )

    result shouldBe expectedRows
  }

  it should "decode complex intermix" in new EnvWithSimpleFile {
    val layout = BinaryDataSetDescription(
      `type` = TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int8
      ),
      files = Seq(
        BinaryFileDescription(
          file.getName,
          content = Seq(
            BinaryFileContent.Skip(3),
            BinaryFileContent.Element("y"),
            BinaryFileContent.Element("x")
          )
        )
      )
    )
    val result = executeReading(layout)

    val expectedRows = Seq(
      TabularRow(Primitive[Int](67438087), Primitive[Byte](3)), // 67438087 = 0x04050607 which is big endian.
      TabularRow(Primitive[Int](202182159), Primitive[Byte](11))
    )

    result shouldBe expectedRows
  }

  it should "decode multiple files" in new EnvWithSimpleFile {
    val layout = BinaryDataSetDescription(
      `type` = TabularData(
        "x" -> FundamentalType.Int8,
        "y" -> FundamentalType.Int8
      ),
      files = Seq(
        BinaryFileDescription(
          file.getName,
          skip = Some(2),
          content = Seq(
            BinaryFileContent.Element("y")
          )
        ),
        BinaryFileDescription(
          file2.getName,
          skip = Some(2),
          content = Seq(
            BinaryFileContent.Element("x")
          )
        )
      )
    )

    val result = executeReading(layout)

    val expectedRows = for (i <- 0 until 14) yield {
      TabularRow(Primitive[Byte]((13 - i).toByte), Primitive[Byte]((i + 2).toByte))
    }

    result shouldBe expectedRows
  }

  it should "handle incomplete files" in new EnvWithSimpleFile {
    val layout = BinaryDataSetDescription(
      `type` = TabularData(
        "x" -> FundamentalType.Uint32
      ),
      files = Seq(
        BinaryFileDescription(
          file.getName,
          content = Seq(
            BinaryFileContent.Element("x"),
            BinaryFileContent.Skip(6)
          )
        )
      )
    )

    val result = executeReading(layout)

    val expectedRows = Seq(
      TabularRow(Primitive[Int](0x00010203))
    )

    result shouldBe expectedRows
  }

  it should "handle read non existant files" in {
    val layout = BinaryDataSetDescription(
      `type` = TabularData(
        "x" -> FundamentalType.Uint32
      ),
      files = Seq(
        BinaryFileDescription(
          "nonexisting",
          content = Seq(
            BinaryFileContent.Element("x"),
            BinaryFileContent.Skip(6)
          )
        )
      )
    )
    val reader = new BinaryFormatReader(layout, tempDirectory)
    val source = reader.read()

    intercept[NoSuchFileException] {
      collectSource(source)
    }
  }

  it should "read mmnist" in {
    val directory = new File("bridge/binary/test/mnist").toPath
    // DS Doesn't know about Mantikfiles, but the definition is equal
    val mantikfile = FileUtils.readFileToString(directory.resolve("Mantikfile").toFile, StandardCharsets.UTF_8)
    val description = YamlParser.parse(mantikfile).forceRight.as[BinaryDataSetDescription].forceRight
    val dataDir = directory.resolve("data")
    val reader = new BinaryFormatReader(description, dataDir)
    val source = reader.read()
    val elements = collectSource(source)
    elements.size shouldBe 10000
  }
}

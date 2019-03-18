package ai.mantik.ds.formats.binary

import ai.mantik.ds._
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.ds.testutil.TestBase
import io.circe.syntax._

import scala.collection.immutable.ListMap

class BinaryDataSetDescriptionSpec extends TestBase {

  val exampleJson =
    """
      |{
      |  "model": {
      |    "columns": {
      |      "x": {
      |        "type": "image",
      |        "width": 28,
      |        "height": 28,
      |        "components": {
      |           "black": {
      |             "componentType": "uint8"
      |           }
      |        }
      |      },
      |      "label": "uint8"
      |    }
      |  },
      |  "files": [
      |    {
      |      "file": "t10k-labels-idx1-ubyte.gz",
      |      "compression": "gzip",
      |      "skip": 8,
      |      "content": [
      |        {"element": "label"},
      |        {"stride": 1}
      |      ]
      |    },
      |    {
      |      "file": "t10k-images-idx3-ubyte.gz",
      |      "compression": "gzip",
      |      "skip": 16,
      |      "content": [
      |        {"element": "x"},
      |        {"stride": 784}
      |      ]
      |    }
      |  ]
      |}
    """.stripMargin

  val exampleType = BinaryDataSetDescription(
    model = TabularData(
      columns = ListMap(
        "x" -> Image(
          28,
          28,
          components = ListMap(
            ImageChannel.Black -> ImageComponent(FundamentalType.Uint8)
          )
        ),
        "label" -> FundamentalType.Uint8
      )
    ),
    files = Seq(
      BinaryFileDescription(
        "t10k-labels-idx1-ubyte.gz",
        Some(Compression.Gzip),
        skip = Some(8),
        content = Seq(
          BinaryFileContent.Element(
            "label"
          ),
          BinaryFileContent.Stride(
            1
          )
        )
      ),
      BinaryFileDescription(
        "t10k-images-idx3-ubyte.gz",
        Some(Compression.Gzip),
        skip = Some(16),
        content = Seq(
          BinaryFileContent.Element(
            "x"
          ),
          BinaryFileContent.Stride(
            784
          )
        )
      )
    )
  )

  it should "convert nicely to and from json" in {
    exampleType.asJson.as[BinaryDataSetDescription].right.get shouldBe exampleType
  }

  it should "be nicely parseable" in {
    val parsed = CirceJson.forceParseJson(exampleJson)
    parsed.as[BinaryDataSetDescription] shouldBe Right(exampleType)
  }
}

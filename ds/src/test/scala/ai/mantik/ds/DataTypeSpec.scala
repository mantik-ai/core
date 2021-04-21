package ai.mantik.ds

import ai.mantik.ds.testutil.TestBase
import io.circe.Json
import io.circe.syntax._

import scala.collection.immutable.ListMap

class DataTypeSpec extends TestBase {

  private def toJson(t: DataType): Json = {
    val result = t.asJson
    result
  }

  "fundamental types" should "serialize well" in {
    toJson(FundamentalType.Int32) shouldBe Json.fromString("int32")
    toJson(FundamentalType.Uint32) shouldBe Json.fromString("uint32")
    toJson(FundamentalType.Float32) shouldBe Json.fromString("float32")
    toJson(FundamentalType.BoolType) shouldBe Json.fromString("bool")
  }

  it should "know from their name" in {
    FundamentalType.fromName("int32") shouldBe Some(FundamentalType.Int32)
    FundamentalType.fromName("INT32") shouldBe Some(FundamentalType.Int32)
    FundamentalType.fromName("unknown") shouldBe None
  }

  "images" should "serialize well" in {
    toJson(
      Image(
        100,
        200,
        ListMap(
          ImageChannel.Red -> ImageComponent(FundamentalType.Uint8),
          ImageChannel.Green -> ImageComponent(FundamentalType.Uint32)
        )
      )
    ) shouldBe Json.obj(
      "type" -> Json.fromString("image"),
      "width" -> 100.asJson,
      "height" -> 200.asJson,
      "components" -> Json.obj(
        "red" -> Json.obj(
          "componentType" -> "uint8".asJson
        ),
        "green" -> Json.obj(
          "componentType" -> "uint32".asJson
        )
      ),
      "format" -> Json.fromString("plain")
    )

    toJson(
      Image(
        100,
        200,
        ListMap(
          ImageChannel.Red -> ImageComponent(FundamentalType.Uint8),
          ImageChannel.Green -> ImageComponent(FundamentalType.Uint32)
        ),
        ImageFormat.Png
      )
    ) shouldBe Json.obj(
      "type" -> Json.fromString("image"),
      "width" -> 100.asJson,
      "height" -> 200.asJson,
      "components" -> Json.obj(
        "red" -> Json.obj(
          "componentType" -> "uint8".asJson
        ),
        "green" -> Json.obj(
          "componentType" -> "uint32".asJson
        )
      ),
      "format" -> "png".asJson
    )
  }

  "imageChannel" should "know their name" in {
    ImageChannel.fromName("red") shouldBe Some(ImageChannel.Red)
    ImageChannel.fromName("BLACK") shouldBe Some(ImageChannel.Black)
    ImageChannel.fromName("unknown") shouldBe None
  }

  "tabular data" should "serialize well" in {
    // Note: the ordering inside the JSON maps is important
    toJson(
      TabularData(
        ListMap(
          "id" -> FundamentalType.Int32,
          "name" -> FundamentalType.StringType
        )
      )
    ) shouldBe Json.obj(
      "columns" -> Json.obj(
        "id" -> "int32".asJson,
        "name" -> "string".asJson
      ),
      "type" -> "tabular".asJson
    )
    toJson(
      TabularData(
        ListMap(
          "image" -> Image(
            100,
            200,
            ListMap(
              ImageChannel.Red -> ImageComponent(FundamentalType.Uint32)
            )
          )
        ),
        rowCount = Some(10000)
      )
    ) shouldBe Json.obj(
      "columns" -> Json.obj(
        "image" -> Json.obj(
          "width" -> 100.asJson,
          "height" -> 200.asJson,
          "components" -> Json.obj(
            "red" -> Json.obj(
              "componentType" -> "uint32".asJson
            )
          ),
          "format" -> "plain".asJson,
          "type" -> "image".asJson
        )
      ),
      "rowCount" -> 10000.asJson,
      "type" -> "tabular".asJson
    )
  }

  "tensors" should "serialize well" in {
    toJson(
      Tensor(
        FundamentalType.Int32,
        Seq(1, 2)
      )
    ) shouldBe Json.obj(
      "type" -> "tensor".asJson,
      "componentType" -> "int32".asJson,
      "shape" -> Json.arr(
        1.asJson,
        2.asJson
      )
    )
  }

  it should "now it's size" in {
    Tensor(FundamentalType.Int32, Seq(1)).packedElementCount shouldBe 1
    Tensor(FundamentalType.Int32, Seq(5)).packedElementCount shouldBe 5
    Tensor(FundamentalType.Int32, Seq(5, 2)).packedElementCount shouldBe 10
    Tensor(FundamentalType.Int32, Seq(5, 2, 3)).packedElementCount shouldBe 30
    Tensor(FundamentalType.Int32, Seq(10, 20, 30, 40, 50, 60, 70, 80)).packedElementCount shouldBe 4032000000000L
  }

  val samples = Seq(
    FundamentalType.Int8,
    FundamentalType.Int32,
    FundamentalType.Int64,
    FundamentalType.Uint8,
    FundamentalType.Uint32,
    FundamentalType.Uint64,
    FundamentalType.StringType,
    FundamentalType.BoolType,
    FundamentalType.Float32,
    FundamentalType.Float64,
    FundamentalType.VoidType,
    Image(
      100,
      200,
      components = ListMap(
        ImageChannel.Red -> ImageComponent(FundamentalType.Int8),
        ImageChannel.Green -> ImageComponent(FundamentalType.Int32),
        ImageChannel.Blue -> ImageComponent(FundamentalType.Int64),
        ImageChannel.Black -> ImageComponent(FundamentalType.Uint64)
      )
    ),
    Image(
      200,
      100,
      components = ListMap(
        // note: switched components
        ImageChannel.Black -> ImageComponent(FundamentalType.Uint64),
        ImageChannel.Green -> ImageComponent(FundamentalType.Int32),
        ImageChannel.Red -> ImageComponent(FundamentalType.Int8),
        ImageChannel.Blue -> ImageComponent(FundamentalType.Int64)
      )
    ),
    Image(
      100,
      100,
      components = ListMap(
        ImageChannel.Black -> ImageComponent(FundamentalType.Uint8)
      ),
      format = ImageFormat.Png
    ),
    TabularData(
      columns = ListMap(
        "id" -> FundamentalType.Int64,
        "name" -> FundamentalType.StringType,
        "image" -> Image(
          100,
          200,
          components = ListMap(
            ImageChannel.Red -> ImageComponent(FundamentalType.Int8)
          )
        )
      )
    ),
    TabularData(
      columns = ListMap(
        "name" -> FundamentalType.StringType,
        "id" -> FundamentalType.Int64,
        "image" -> Image(
          100,
          200,
          components = ListMap(
            ImageChannel.Red -> ImageComponent(FundamentalType.Int8)
          )
        )
      ),
      rowCount = Some(435432353455L)
    ),
    Tensor(
      componentType = FundamentalType.BoolType,
      shape = Seq(1, 2, 3)
    ),
    Tensor(
      componentType = FundamentalType.Int32,
      shape = Seq(1)
    ),
    Nullable(FundamentalType.Int32),
    Nullable(
      Tensor(
        componentType = FundamentalType.Uint8,
        shape = Seq(1, 2)
      )
    ),
    ArrayT(FundamentalType.Int32),
    Struct(
      "x" -> FundamentalType.Int32,
      "y" -> ArrayT(FundamentalType.StringType)
    )
  )

  "deserialization" should "work" in {
    for {
      sample <- samples
    } {
      val json = toJson(sample)
      json.as[DataType] shouldBe Right(sample)
    }
  }

  "toJsonString" should "work without any extra spaces" in {
    for {
      sample <- samples
    } {
      sample.toJsonString shouldBe toJson(sample).noSpaces
    }
  }
}

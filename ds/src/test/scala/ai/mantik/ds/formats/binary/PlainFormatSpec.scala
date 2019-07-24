package ai.mantik.ds.formats.binary

import java.nio.ByteOrder

import ai.mantik.ds.element.Primitive
import ai.mantik.ds._
import ai.mantik.ds.testutil.TestBase
import akka.util.ByteString

import scala.collection.immutable.ListMap

class PlainFormatSpec extends TestBase {

  "plainSize" should "know the plain size of data formats" in {
    for {
      (dt, size) <- Seq(
        FundamentalType.BoolType -> 1,
        FundamentalType.Int8 -> 1,
        FundamentalType.Int32 -> 4,
        FundamentalType.Int64 -> 8,
        FundamentalType.Uint8 -> 1,
        FundamentalType.Uint32 -> 4,
        FundamentalType.Uint64 -> 8,
        FundamentalType.Float32 -> 4,
        FundamentalType.Float64 -> 8
      )
    } {
      withClue(s"For type ${dt}") {
        PlainFormat.plainSize(dt) shouldBe Some(size)
      }
    }
  }

  it should "know when a type has no fundamental size" in {
    PlainFormat.plainSize(FundamentalType.StringType) shouldBe None
  }

  it should "calculate the size of images" in {
    val image1 = Image(
      4, 5, components = ListMap(
        ImageChannel.Red -> ImageComponent(FundamentalType.Uint8),
        ImageChannel.Green -> ImageComponent(FundamentalType.Int8)
      )
    )
    val image2 = Image(
      4, 5, components = ListMap(
        ImageChannel.Red -> ImageComponent(FundamentalType.StringType)
      )
    )
    PlainFormat.plainSize(image1) shouldBe Some(4 * 5 * 2)
    PlainFormat.plainSize(image2) shouldBe None
  }

  it should "not know the size of non plain images" in {
    PlainFormat.plainSize(
      Image(3, 4, components = ListMap(
        ImageChannel.Black -> ImageComponent(FundamentalType.Uint8)
      ), ImageFormat.Png)
    ) shouldBe None
  }

  val data = ByteString(1, 2, 3, 4, 7, 8, 9, 10)

  "plainDecoder" should "return nice decoders for big endian" in {
    implicit val bo = ByteOrder.BIG_ENDIAN
    PlainFormat.plainDecoder(FundamentalType.BoolType).get(data.iterator) shouldBe Primitive(true)
    PlainFormat.plainDecoder(FundamentalType.Uint8).get(data.iterator) shouldBe Primitive(1)
    PlainFormat.plainDecoder(FundamentalType.Int32).get(data.iterator) shouldBe Primitive(0x01020304)
    PlainFormat.plainDecoder(FundamentalType.Float32) shouldBe defined
    PlainFormat.plainDecoder(FundamentalType.Float64) shouldBe defined
  }

  it should "work for little endian" in {
    implicit val bo = ByteOrder.LITTLE_ENDIAN
    PlainFormat.plainDecoder(FundamentalType.BoolType).get(data.iterator) shouldBe Primitive(true)
    PlainFormat.plainDecoder(FundamentalType.Uint8).get(data.iterator) shouldBe Primitive(1)
    PlainFormat.plainDecoder(FundamentalType.Int32).get(data.iterator) shouldBe Primitive(0x04030201)
    PlainFormat.plainDecoder(FundamentalType.Float32) shouldBe defined
    PlainFormat.plainDecoder(FundamentalType.Float64) shouldBe defined
  }

  it should "work for images " in {
    implicit val bo = ByteOrder.BIG_ENDIAN
    val image = Image(
      2, 3, components = ListMap(
        ImageChannel.Red -> ImageComponent(FundamentalType.Uint8)
      )
    )
    PlainFormat.plainDecoder(image).get(data.iterator) shouldBe element.ImageElement(
      data.take(2 * 3)
    )
  }
}

package ai.mantik.ds.converter

import ai.mantik.ds.element.{ ImageElement, Primitive, TensorElement, ValueEncoder }
import ai.mantik.ds._
import ai.mantik.testutils.TestBase
import akka.util.ByteString

import scala.collection.immutable.ListMap

class CastSpec extends TestBase {

  it should "have identity" in {
    val id = Cast.findCast(FundamentalType.Uint8, FundamentalType.Uint8).right.get
    id.canFail shouldBe false
    id.loosing shouldBe false
    id.op(ValueEncoder.wrap(8.toByte)) shouldBe Primitive(8)
  }

  it should "work for simple conversions" in {
    val c1 = Cast.findCast(FundamentalType.Int32, FundamentalType.Int64).right.get
    c1.canFail shouldBe false
    c1.loosing shouldBe false
    c1.op(ValueEncoder.wrap(123)) shouldBe ValueEncoder.wrap(123L)

    val c2 = Cast.findCast(FundamentalType.Int64, FundamentalType.Int32).right.get
    c2.canFail shouldBe false
    c2.loosing shouldBe true
    c2.op(ValueEncoder.wrap(123L)) shouldBe ValueEncoder.wrap(123)
  }

  it should "cast 8 bit integers to float32" in {
    val c1 = Cast.findCast(FundamentalType.Uint8, FundamentalType.Float32).right.get
    c1.op(Primitive(200.toByte)) shouldBe Primitive(200.toFloat)
    c1.canFail shouldBe false
    c1.loosing shouldBe false // 23 bits of precision in float32

    val c2 = Cast.findCast(FundamentalType.Int8, FundamentalType.Float32).right.get
    c2.op(Primitive(-10.toByte)) shouldBe Primitive(-10.toFloat)
    c2.canFail shouldBe false
    c2.loosing shouldBe false // 23 bits of precision in float32
  }

  it should "cast 32 bit integers to float64" in {
    val c1 = Cast.findCast(FundamentalType.Uint32, FundamentalType.Float64).right.get
    c1.op(Primitive(4000000000L.toInt)) shouldBe Primitive(4000000000L.toDouble)
    c1.canFail shouldBe false
    c1.loosing shouldBe false // 52 bits of precision in float32

    val c2 = Cast.findCast(FundamentalType.Int32, FundamentalType.Float64).right.get
    c2.op(Primitive(-200)) shouldBe Primitive(-200.toDouble)
    c2.canFail shouldBe false
    c2.loosing shouldBe false // 52 bits of precision in float32
  }

  it should "work for chained conversions" in {
    val c1 = Cast.findCast(FundamentalType.Int8, FundamentalType.Int64).right.get
    c1.loosing shouldBe false
    c1.canFail shouldBe false
    c1.op(ValueEncoder.wrap(8.toByte)) shouldBe ValueEncoder.wrap(8L)

    val c2 = Cast.findCast(FundamentalType.Uint8, FundamentalType.Int32).right.get
    c2.loosing shouldBe false
    c2.canFail shouldBe false
    c2.op(ValueEncoder.wrap(-1.toByte)) shouldBe ValueEncoder.wrap(255)
  }

  it should "convert everything to string and back" in {
    for ((ft, value) <- TypeSamples.fundamentalSamples) {
      withClue(s"It should work for ${ft}") {
        val toString = Cast.findCast(ft, FundamentalType.StringType).right.getOrElse(fail())
        val fromString = Cast.findCast(FundamentalType.StringType, ft).right.getOrElse(fail())
        val asString = toString.op(value)
        fromString.op(asString) shouldBe value
      }
    }
  }

  it should "cast from primitive to tensor" in {
    val c = Cast.findCast(FundamentalType.Int32, Tensor(FundamentalType.Int64, List(1))).right.getOrElse(fail())
    c.from shouldBe FundamentalType.Int32
    c.to shouldBe Tensor(FundamentalType.Int64, List(1))
    c.loosing shouldBe false
    c.canFail shouldBe false
    c.op(ValueEncoder.wrap(3)) shouldBe TensorElement(IndexedSeq(3L))

    Cast.findCast(FundamentalType.Int32, Tensor(FundamentalType.Int64, List(2, 3))).isLeft shouldBe true // invalid shape
  }

  it should "cast tensors to primitives" in {
    val c = Cast.findCast(Tensor(FundamentalType.Int64, List(1)), FundamentalType.Int32).right.getOrElse(fail())
    c.from shouldBe Tensor(FundamentalType.Int64, List(1))
    c.to shouldBe FundamentalType.Int32
    c.loosing shouldBe true
    c.canFail shouldBe false
    c.op(TensorElement(IndexedSeq(3L))) shouldBe ValueEncoder.wrap(3)

    Cast.findCast(Tensor(FundamentalType.Int64, List(2, 3)), FundamentalType.Int32).isLeft shouldBe true // invalid shape
  }

  it should "cast images to tensors" in {
    val c = Cast.findCast(TypeSamples.image._1, Tensor(FundamentalType.Int32, List(3, 2))).right.getOrElse(fail())
    c.from shouldBe TypeSamples.image._1
    c.to shouldBe Tensor(FundamentalType.Int32, List(3, 2))
    c.loosing shouldBe false
    c.canFail shouldBe false
    c.op(TypeSamples.image._2) shouldBe TensorElement(IndexedSeq(1, 2, 3, 4, 5, 6))
    Cast.findCast(TypeSamples.image._1, Tensor(FundamentalType.Int32, List(3, 3))).isLeft shouldBe true
  }

  it should "cast tensors to images" in {
    val c = Cast.findCast(Tensor(FundamentalType.Int32, List(3, 2)), TypeSamples.image._1).right.getOrElse(fail())
    c.from shouldBe Tensor(FundamentalType.Int32, List(3, 2))
    c.to shouldBe TypeSamples.image._1
    c.loosing shouldBe true
    c.canFail shouldBe false
    c.op(TensorElement(IndexedSeq[Int](1, 2, 3, 4, 5, 6))) shouldBe TypeSamples.image._2
  }

  it should "cast tensors to flat tensors" in {
    val c = Cast.findCast(
      Tensor(FundamentalType.Int32, List(3, 2)),
      Tensor(FundamentalType.Int32, List(6))
    ).right.getOrElse(fail())
    c.canFail shouldBe false
    c.loosing shouldBe false
    c.from shouldBe Tensor(FundamentalType.Int32, List(3, 2))
    c.to shouldBe Tensor(FundamentalType.Int32, List(6))
    c.op(TensorElement[Int](IndexedSeq(1, 2, 3, 4, 5, 6))) shouldBe TensorElement[Int](IndexedSeq(1, 2, 3, 4, 5, 6))

  }

  it should "cast tensor sub types" in {
    val c = Cast.findCast(
      Tensor(FundamentalType.Int32, List(3, 2)),
      Tensor(FundamentalType.Int8, List(2, 3))
    ).right.getOrElse(fail())
    c.canFail shouldBe false
    c.loosing shouldBe true
    c.from shouldBe Tensor(FundamentalType.Int32, List(3, 2))
    c.to shouldBe Tensor(FundamentalType.Int8, List(2, 3))
    c.op(TensorElement[Int](IndexedSeq(1, 2, 3, 4, 5, 6))) shouldBe TensorElement[Byte](IndexedSeq(1, 2, 3, 4, 5, 6))
  }

  it should "cast image sub types" in {
    val from = TypeSamples.image._1
    val to = Image(
      2, 3, ListMap(ImageChannel.Red -> ImageComponent(FundamentalType.Int32))
    )
    val invalid1 = Image(
      3, 3, ListMap(ImageChannel.Black -> ImageComponent(FundamentalType.Uint8))
    ) // wrong size

    val invalid2 = Image(
      2, 3, ListMap(
        ImageChannel.Black -> ImageComponent(FundamentalType.Uint8),
        ImageChannel.Blue -> ImageComponent(FundamentalType.Uint8)
      )
    ) // multiple components (not yet supported)
    Cast.findCast(from, invalid1).isLeft shouldBe true
    Cast.findCast(from, invalid2).isLeft shouldBe true
    Cast.findCast(invalid1, to).isLeft shouldBe true
    Cast.findCast(invalid2, to).isLeft shouldBe true

    val c = Cast.findCast(from, to).right.getOrElse(fail())
    c.canFail shouldBe false
    c.loosing shouldBe false
    val casted = c.op(TypeSamples.image._2).asInstanceOf[ImageElement]
    casted.bytes shouldBe ByteString(
      0, 0, 0, 1,
      0, 0, 0, 2,
      0, 0, 0, 3,
      0, 0, 0, 4,
      0, 0, 0, 5,
      0, 0, 0, 6
    )
  }
}
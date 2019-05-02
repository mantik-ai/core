package ai.mantik.ds.converter.casthelper

import ai.mantik.ds._
import ai.mantik.ds.element.{ ImageElement, Primitive, TensorElement }
import ai.mantik.ds.testutil.TestBase
import akka.util.ByteString

import scala.collection.immutable.ListMap

class ImageHelperSpec extends TestBase {

  "imageUnpacker" should "work" in {
    val unpacker = ImageHelper.imageUnpacker(TypeSamples.image._1).right.getOrElse(fail())

    val unpacked = unpacker(TypeSamples.image._2)
    unpacked shouldBe IndexedSeq(
      Primitive(1),
      Primitive(2),
      Primitive(3),
      Primitive(4),
      Primitive(5),
      Primitive(6)
    )

    val back = ImageHelper.imagePacker(TypeSamples.image._1).right.getOrElse(fail())
    back(unpacked) shouldBe TypeSamples.image._2
  }

  it should "work for complexer types" in {
    val image = Image(2, 3, ListMap(ImageChannel.Red -> ImageComponent(FundamentalType.Int32)))
    val data = ImageElement(
      ByteString(0 until 24: _*)
    )
    val unpacker = ImageHelper.imageUnpacker(image).right.getOrElse(fail())
    val unpacked = unpacker(data)

    unpacked shouldBe IndexedSeq(
      Primitive(0x00010203),
      Primitive(0x04050607),
      Primitive(0x08090a0b),
      Primitive(0x0c0d0e0f),
      Primitive(0x10111213),
      Primitive(0x14151617)
    )
    val packer = ImageHelper.imagePacker(image).right.getOrElse(fail())
    packer(unpacked) shouldBe data
  }
}

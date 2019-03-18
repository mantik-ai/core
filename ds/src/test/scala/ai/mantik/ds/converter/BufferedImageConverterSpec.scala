package ai.mantik.ds.converter

import java.awt.image.BufferedImage

import ai.mantik.ds.FundamentalType.{ Float32, Int8, Uint8 }
import ai.mantik.ds.natural.ImageElement
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{ Image, ImageChannel, ImageComponent }
import akka.util.ByteString

import scala.collection.immutable.ListMap
import scala.util.Random

class BufferedImageConverterSpec extends TestBase {

  it should "convert a simple greyscale image" in {
    val image = Image(
      4, 5,
      components = ListMap(
        ImageChannel.Black -> ImageComponent(
          Uint8
        )
      )
    )
    val sample = ImageElement(
      ByteString(
        1, 2, 3, 4,
        5, 6, 7, 8,
        9, 10, 11, 12,
        13, 14, 15, 16,
        17, 18, 19, 20
      )
    )
    val converter = new BufferedImageConverter(image)
    converter.isRgbOnly shouldBe false
    converter.isBlackOnly shouldBe true
    converter.canHandle shouldBe true

    val converted = converter.convert(sample)
    converted.getWidth shouldBe 4
    converted.getHeight shouldBe 5
    converted.getType shouldBe BufferedImage.TYPE_BYTE_GRAY
    converted.getData.getPixel(0, 0, null: Array[Int])(0) shouldBe 1
    converted.getData.getPixel(3, 4, null: Array[Int])(0) shouldBe 20
    converted.getData.getPixel(2, 3, null: Array[Int])(0) shouldBe 15
  }

  it should "convert a simple rgb image" in {
    val image = Image(
      2, 3,
      components = ListMap(
        ImageChannel.Red -> ImageComponent(Int8),
        ImageChannel.Green -> ImageComponent(Int8),
        ImageChannel.Blue -> ImageComponent(Int8)
      )
    )
    val sample = ImageElement(
      ByteString(
        // 2 x 3 x 3 = 18 Values
        1, 2, 3, 4, 5, 6,
        7, 8, 9, 10, 11, 12,
        13, 14, 15, 16, 17, 18
      )
    )
    val converter = new BufferedImageConverter(image)
    converter.isRgbOnly shouldBe true
    converter.isBlackOnly shouldBe false
    converter.canHandle shouldBe true

    val converted = converter.convert(sample)
    converted.getWidth shouldBe 2
    converted.getHeight shouldBe 3
    converted.getType shouldBe BufferedImage.TYPE_INT_RGB
    converted.getData.getPixel(0, 0, null: Array[Int]).toSeq shouldBe Seq(1, 2, 3)
    converted.getData.getPixel(1, 2, null: Array[Int]).toSeq shouldBe Seq(16, 17, 18)
    converted.getData.getPixel(1, 1, null: Array[Int]).toSeq shouldBe Seq(10, 11, 12)
  }

  it should "flip rgb images with different order" in {
    val image = Image(
      2, 3,
      components = ListMap(
        ImageChannel.Blue -> ImageComponent(Int8),
        ImageChannel.Green -> ImageComponent(Int8),
        ImageChannel.Red -> ImageComponent(Int8)
      )
    )
    val sample = ImageElement(
      ByteString(
        // 2 x 3 x 3 = 18 Values
        1, 2, 3, 4, 5, 6,
        7, 8, 9, 10, 11, 12,
        13, 14, 15, 16, 17, 18
      )
    )
    val converter = new BufferedImageConverter(image)
    converter.isRgbOnly shouldBe true
    converter.isBlackOnly shouldBe false
    converter.canHandle shouldBe true

    val converted = converter.convert(sample)
    converted.getWidth shouldBe 2
    converted.getHeight shouldBe 3
    converted.getType shouldBe BufferedImage.TYPE_INT_RGB
    converted.getData.getPixel(0, 0, null: Array[Int]).toSeq shouldBe Seq(3, 2, 1)
    converted.getData.getPixel(1, 2, null: Array[Int]).toSeq shouldBe Seq(18, 17, 16)
    converted.getData.getPixel(1, 1, null: Array[Int]).toSeq shouldBe Seq(12, 11, 10)
  }

  it should "convert a simple red only image" in {
    val image = Image(
      2, 3,
      components = ListMap(
        ImageChannel.Red -> ImageComponent(Int8)
      )
    )
    val sample = ImageElement(
      ByteString(
        1, 2,
        7, 8,
        13, 14
      )
    )
    val converter = new BufferedImageConverter(image)
    converter.isRgbOnly shouldBe true
    converter.isBlackOnly shouldBe false
    converter.canHandle shouldBe true

    val converted = converter.convert(sample)
    converted.getWidth shouldBe 2
    converted.getHeight shouldBe 3
    converted.getType shouldBe BufferedImage.TYPE_INT_RGB
    converted.getData.getPixel(0, 0, null: Array[Int]).toSeq shouldBe Seq(1, 0, 0)
    converted.getData.getPixel(1, 2, null: Array[Int]).toSeq shouldBe Seq(14, 0, 0)
    converted.getData.getPixel(1, 1, null: Array[Int]).toSeq shouldBe Seq(8, 0, 0)
  }

  it should "handle an 1MB image" in {
    val image = Image(
      1000, 1000,
      components = ListMap(
        ImageChannel.Red -> ImageComponent(Int8),
        ImageChannel.Green -> ImageComponent(Int8),
        ImageChannel.Blue -> ImageComponent(Int8)
      )
    )
    val bytes = new Array[Byte](image.width * image.height * 3)
    Random.nextBytes(bytes)
    val sample = ImageElement(
      ByteString(bytes)
    )
    val converter = new BufferedImageConverter(image)
    converter.isRgbOnly shouldBe true
    converter.isBlackOnly shouldBe false
    converter.canHandle shouldBe true

    val converted = converter.convert(sample)
    converted.getWidth shouldBe image.width
    converted.getHeight shouldBe image.height
    converted.getType shouldBe BufferedImage.TYPE_INT_RGB
  }

  it should "reject other types" in {
    val wrongType = Image(
      1000, 1000,
      components = ListMap(
        ImageChannel.Red -> ImageComponent(Float32)
      )
    )
    new BufferedImageConverter(wrongType).canHandle shouldBe false

    val blackAndColorMixed = Image(
      1000, 1000,
      components = ListMap(
        ImageChannel.Red -> ImageComponent(Int8),
        ImageChannel.Black -> ImageComponent(Int8)
      )
    )
    new BufferedImageConverter(blackAndColorMixed).canHandle shouldBe false
  }
}

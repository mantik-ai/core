package ai.mantik.ds.converter

import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image._

import ai.mantik.ds.{ FundamentalType, Image, ImageChannel, ImageFormat }
import ai.mantik.ds.natural
import akka.util.ByteIterator
import org.slf4j.LoggerFactory

/**
 * Responsible for converting Image data into BufferedImages to render previews.
 * Note:
 *
 * - image quality is reduced, only 8 bit channels are supported
 */
private[converter] class BufferedImageConverter(image: Image) {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Is true, if the iamge can be handled. */
  def canHandle: Boolean = {
    image.width > 0 && image.height > 1 && canHandleChannels() && canHandleTypes && image.format == ImageFormat.Plain
  }

  private val channels: Set[ImageChannel] = image.components.keySet

  lazy val isBlackOnly = channels == Set(ImageChannel.Black)
  lazy val isRgbOnly = !channels.contains(ImageChannel.Black) && channels.nonEmpty &&
    channels.diff(
      Set(
        ImageChannel.Red,
        ImageChannel.Green,
        ImageChannel.Blue
      )
    ).isEmpty

  lazy val singleComponentType = image.components.values.map(_.componentType).toSeq.distinct match {
    case Seq(singleType) => Some(singleType)
    case _               => None
  }

  private lazy val canHandleTypes = singleComponentType match {
    // we only support one byte per channel, right now.
    case Some(FundamentalType.Uint8) => true
    case Some(FundamentalType.Int8)  => true
    case _                           => false
  }

  private def canHandleChannels(): Boolean = {
    isBlackOnly || isRgbOnly
  }

  def convert(imageData: natural.ImageElement): BufferedImage = {
    val t0 = System.currentTimeMillis()

    require(canHandle, "This image can't be handled")

    if (isBlackOnly && singleComponentType.contains(FundamentalType.Uint8)) {
      // Fast track
      return convertGrayScaleUint8(imageData)
    }
    if (isRgbOnly &&
      (channels == Set(ImageChannel.Red, ImageChannel.Green, ImageChannel.Blue)) &&
      (singleComponentType.contains(FundamentalType.Uint8) || singleComponentType.contains(FundamentalType.Int8))) {
      // Fast track
      return convertRgbUint8(imageData)
    }

    val imageType = if (isRgbOnly) {
      BufferedImage.TYPE_INT_RGB
    } else if (isBlackOnly) {
      BufferedImage.TYPE_BYTE_GRAY
    } else {
      throw new IllegalStateException(s"Cannot convert this channels, call canHandleChannels first")
    }
    val result = new BufferedImage(
      image.width, image.height, imageType
    )
    val raster = result.getRaster
    fillRaster(raster, imageData)
    result.setData(raster)

    val t1 = System.currentTimeMillis()
    logger.debug(s"Converting image ${image.width}x${image.height} took ${t1 - t0}ms")
    result
  }

  private def convertGrayScaleUint8(imageData: natural.ImageElement): BufferedImage = {
    val cs = ColorSpace.getInstance(ColorSpace.CS_GRAY)
    val nBits = Array(8)
    val colorModel = new ComponentColorModel(cs, nBits, false, true, Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
    val raster = colorModel.createCompatibleWritableRaster(image.width, image.height)
    val buffer = raster.getDataBuffer.asInstanceOf[DataBufferByte]
    imageData.bytes.copyToArray(
      buffer.getData
    )
    new BufferedImage(colorModel, raster, colorModel.isAlphaPremultiplied, null)
  }

  // This doesn't easily work, as we still have to unwrap 3 into 4 bytes
  private def convertRgbUint8(imageData: natural.ImageElement): BufferedImage = {
    require(
      imageData.bytes.length == image.width * image.height * 3, "Pixel data length must match"
    )

    val result = new BufferedImage(
      image.width, image.height, BufferedImage.TYPE_INT_RGB
    )
    val buffer = result.getRaster.getDataBuffer.asInstanceOf[DataBufferInt]

    var i = 0
    val pixelCount = image.width * image.height
    val it = imageData.bytes.iterator
    val resultArray = buffer.getData

    // Target XRGB

    def shiftForByte(id: Int): Int = {
      val channel = image.components.keysIterator.toSeq(id)
      channel match {
        case ImageChannel.Red   => 16
        case ImageChannel.Green => 8
        case ImageChannel.Blue  => 0
        case other =>
          throw new IllegalStateException(s"Unexpected channel ${channel}")
      }
    }

    val byte1Shift = shiftForByte(0)
    val byte2Shift = shiftForByte(1)
    val byte3Shift = shiftForByte(2)

    while (i < pixelCount) {
      val pixel1 = it.getByte.toInt
      val pixel2 = it.getByte.toInt
      val pixel3 = it.getByte.toInt
      val resultPixel = (pixel1 << byte1Shift) + (pixel2 << byte2Shift) + (pixel3 << byte3Shift)
      resultArray(i) = resultPixel
      i += 1
    }
    result
  }

  private def fillRaster(raster: WritableRaster, imageData: natural.ImageElement): Unit = {
    require(image.width >= 0)
    require(image.height >= 0)
    if (isBlackOnly) {
      fillRasterGreyscale(raster, imageData)
    } else if (isRgbOnly) {
      fillRasterRgb(raster, imageData)
    } else {
      throw new IllegalStateException(s"Should not come here")
    }
  }

  private def fillRasterGreyscale(raster: WritableRaster, imageData: natural.ImageElement): Unit = {
    // This is probably so slow as it looks

    val it = imageData.bytes.iterator
    var y = 0
    while (y < image.height) {
      var x = 0
      while (x < image.width) {
        raster.setPixel(x, y, Array(it.getByte.toInt))
        x += 1
      }
      y += 1
    }
  }

  private def fillRasterRgb(raster: WritableRaster, imageData: natural.ImageElement): Unit = {
    // This is probably so slow as it looks
    val rgbComponentCount = image.components.size
    val referenceOrder = Seq(
      ImageChannel.Red,
      ImageChannel.Green,
      ImageChannel.Blue
    )
    val componentTranslation = image.components.keys.map { channel =>
      referenceOrder.indexOf(channel).ensuring(_ != -1, "Unsupported channel")
    }.toArray

    val buffer = new Array[Int](3)

    def fetcher(iterator: ByteIterator): Unit = {
      var i = 0
      while (i < rgbComponentCount) {
        val data = iterator.getByte
        buffer(componentTranslation(i)) = data
        i += 1
      }
    }

    val it = imageData.bytes.iterator
    var y = 0
    while (y < image.height) {
      var x = 0
      while (x < image.width) {
        fetcher(it)
        raster.setPixel(x, y, buffer)
        x += 1
      }
      y += 1
    }
  }

}

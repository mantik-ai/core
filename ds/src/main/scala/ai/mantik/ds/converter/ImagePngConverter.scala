package ai.mantik.ds.converter

import ai.mantik.ds.element.{Element, ImageElement}
import ai.mantik.ds.{DataType, Image, ImageFormat}

/** Converts images into Image PNG Data. */
class ImagePngConverter(originalImage: Image, targetWidth: Int, targetHeight: Int) extends DataTypeConverter {
  val subConverter = new BufferedImageConverter(originalImage)

  require(
    subConverter.canHandle
  )

  override def targetType: DataType = {
    originalImage.copy(
      width = targetWidth,
      height = targetHeight,
      format = ImageFormat.Png
    )
  }

  override def convert(element: Element): Element = {
    val imageData = element.asInstanceOf[ImageElement]

    val asBufferedImage = subConverter.convert(imageData)
    val scaled = ImagePreviewHelper.scaleSize(asBufferedImage, targetWidth, targetHeight)
    val pngData = ImagePreviewHelper.createPngData(scaled)
    ImageElement(
      pngData
    )
  }
}

object ImagePngConverter {

  /** Returns true if the image can converted. */
  def canHandle(image: Image): Boolean = {
    new BufferedImageConverter(image).canHandle
  }
}

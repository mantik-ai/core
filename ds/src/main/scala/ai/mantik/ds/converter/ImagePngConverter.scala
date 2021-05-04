/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
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

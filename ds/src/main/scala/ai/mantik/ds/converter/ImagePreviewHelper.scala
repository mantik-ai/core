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

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

import akka.util.ByteString
import javax.imageio.ImageIO

/** Helper for handling scaling and conversion of [[BufferedImage]] */
private[converter] object ImagePreviewHelper {

  def createPngData(bufferedImage: BufferedImage): ByteString = {
    val outputStream = new ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "png", outputStream)
    outputStream.close()
    ByteString.fromArrayUnsafe(outputStream.toByteArray)
  }

  def scaleSize(bufferedImage: BufferedImage, width: Int, height: Int): BufferedImage = {
    if (bufferedImage.getWidth == width && bufferedImage.getHeight == height) {
      return bufferedImage
    }
    // See https://stackoverflow.com/questions/9417356/bufferedimage-resize
    val scaledImage = bufferedImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)
    val result = new BufferedImage(width, height, bufferedImage.getType)
    val gcontext = result.createGraphics()
    gcontext.drawImage(scaledImage, 0, 0, null)
    gcontext.dispose()
    result
  }

  def limitSize(w: Int, h: Int, maxWidth: Int, maxHeight: Int): (Int, Int) = {
    if (w <= maxWidth && h <= maxHeight) {
      (w, h)
    } else {
      scaleSizes(w, h, maxWidth, maxHeight)
    }
  }

  /** Calculates width / height of an image of size w,h which should be scaled down to expectedWidth, expectedHeight. */
  def scaleSizes(w: Int, h: Int, maxWidth: Int, maxHeight: Int): (Int, Int) = {
    val givenRatio = w.toDouble / h.toDouble
    val expectedRatio = maxWidth.toDouble / maxHeight.toDouble

    val (newWidth, newHeight) = if (givenRatio > expectedRatio) {
      (maxWidth, (maxWidth / givenRatio).toInt)
    } else {
      ((maxHeight * givenRatio).toInt, maxHeight)
    }

    Math.min(newWidth, maxWidth) -> Math.min(newHeight, maxHeight)
  }
}

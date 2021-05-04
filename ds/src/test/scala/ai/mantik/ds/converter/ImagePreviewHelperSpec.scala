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

import java.awt.image.BufferedImage

import ai.mantik.ds.testutil.TestBase
import akka.util.ByteString

class ImagePreviewHelperSpec extends TestBase {

  "createPngData" should "work" in {
    val a = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB)
    val converted = ImagePreviewHelper.createPngData(a)
    converted shouldNot be(empty)
    converted.take(8) shouldBe ByteString(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
  }

  "scaleSize" should "work" in {
    val a = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB)
    val converted = ImagePreviewHelper.scaleSize(a, 100, 120)
    converted.getType shouldBe BufferedImage.TYPE_INT_RGB
    converted.getWidth() shouldBe 100
    converted.getHeight() shouldBe 120
  }

  "limitSize" should "work" in {
    val (w, h) = ImagePreviewHelper.limitSize(3000, 2000, 100, 100)
    w shouldBe 100
    h shouldBe 66
  }

  it should "do nothing if the image is smaller" in {
    val (w, h) = ImagePreviewHelper.limitSize(70, 60, 100, 100)
    w shouldBe 70
    h shouldBe 60
  }

  "scaleSizes" should "work" in {
    ImagePreviewHelper.scaleSizes(
      3000,
      2000,
      100,
      100
    ) shouldBe (100, 66)

    ImagePreviewHelper.scaleSizes(
      2000,
      3000,
      100,
      100
    ) shouldBe (66, 100)

    ImagePreviewHelper.scaleSizes(
      3000,
      2000,
      300,
      200
    ) shouldBe (300, 200)

    ImagePreviewHelper.scaleSizes(
      3000,
      2000,
      200,
      300
    ) shouldBe (
      200, 133
    )
  }
}

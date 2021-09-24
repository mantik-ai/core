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
package ai.mantik.ds.converter.casthelper

import ai.mantik.ds._
import ai.mantik.ds.element.{ImageElement, Primitive, TensorElement}
import ai.mantik.ds.testutil.TestBase
import akka.util.ByteString

import scala.collection.immutable.ListMap

class ImageHelperSpec extends TestBase {

  "imageUnpacker" should "work" in {
    val unpacker = ImageHelper.imageUnpacker(TypeSamples.image._1).forceRight

    val unpacked = unpacker(TypeSamples.image._2)
    unpacked shouldBe IndexedSeq(
      Primitive(1),
      Primitive(2),
      Primitive(3),
      Primitive(4),
      Primitive(5),
      Primitive(6)
    )

    val back = ImageHelper.imagePacker(TypeSamples.image._1).forceRight
    back(unpacked) shouldBe TypeSamples.image._2
  }

  it should "work for complexer types" in {
    val image = Image(2, 3, ListMap(ImageChannel.Red -> ImageComponent(FundamentalType.Int32)))
    val data = ImageElement(
      ByteString(0 until 24: _*)
    )
    val unpacker = ImageHelper.imageUnpacker(image).forceRight
    val unpacked = unpacker(data)

    unpacked shouldBe IndexedSeq(
      Primitive(0x00010203),
      Primitive(0x04050607),
      Primitive(0x08090a0b),
      Primitive(0x0c0d0e0f),
      Primitive(0x10111213),
      Primitive(0x14151617)
    )
    val packer = ImageHelper.imagePacker(image).forceRight
    packer(unpacked) shouldBe data
  }
}

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
package ai.mantik.ds

import ai.mantik.ds.FundamentalType._
import ai.mantik.ds.element.{Element, ImageElement, ArrayElement, StructElement, Primitive}
import akka.util.ByteString

import scala.collection.immutable.ListMap

object TypeSamples {

  val fundamentalSamples: Seq[(FundamentalType, Primitive[_])] = Seq(
    BoolType -> Primitive(true),
    Int8 -> Primitive(34.toByte),
    Int8 -> Primitive(-1.toByte),
    Uint8 -> Primitive(254.toByte),
    Uint8 -> Primitive(128.toByte),
    Int32 -> Primitive(453583),
    Int32 -> Primitive(-25359234),
    Uint32 -> Primitive(35452343424L.toInt),
    Uint32 -> Primitive(-1),
    Uint32 -> Primitive(363463568),
    Int64 -> Primitive(Long.MaxValue),
    Int64 -> Primitive(Long.MinValue),
    Uint64 -> Primitive(-1.toLong),
    Uint64 -> Primitive(453458430584358L),
    Float32 -> Primitive(1.5f),
    Float32 -> Primitive(-1.5f),
    Float32 -> Primitive(Float.NegativeInfinity),
    Float64 -> Primitive(-1.5),
    Float64 -> Primitive(1.5),
    Float64 -> Primitive(Double.NegativeInfinity),
    StringType -> Primitive("Hello World"),
    VoidType -> Primitive.unit
  )

  def nonFundamentals: Seq[(DataType, Element)] = Seq(image, array, namedTuple)

  val image = (
    Image(2, 3, ListMap(ImageChannel.Black -> ImageComponent(FundamentalType.Uint8))),
    ImageElement(ByteString(1, 2, 3, 4, 5, 6))
  )

  val array = (
    ArrayT(FundamentalType.Int32),
    ArrayElement(IndexedSeq(Primitive(2), Primitive(4)))
  )

  val namedTuple = (
    Struct(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.StringType
    ),
    StructElement(IndexedSeq(Primitive(3), Primitive("World")))
  )
}

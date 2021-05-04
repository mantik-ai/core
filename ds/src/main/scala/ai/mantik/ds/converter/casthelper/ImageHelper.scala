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

import java.nio.ByteOrder

import ai.mantik.ds.element.{ImageElement, Primitive}
import ai.mantik.ds.{FundamentalType, Image, ImageFormat}
import akka.util.{ByteIterator, ByteString, ByteStringBuilder}

/** Packs and unpacks images. */
private[converter] object ImageHelper {
  private implicit val order = ByteOrder.BIG_ENDIAN

  def imageUnpacker(image: Image): Either[String, ImageElement => IndexedSeq[Primitive[_]]] = {
    val singleComponent = image.components.map(_._2.componentType) match {
      case List(single) => single
      case _            => return Left("Can only convert single component images to tensors")
    }
    if (image.format != ImageFormat.Plain) {
      return Left("Only plain images can be converted")
    }
    buildReader(singleComponent).map { reader => i: ImageElement =>
      {
        val builder = IndexedSeq.newBuilder[Primitive[_]]
        builder.sizeHint(image.width * image.height)
        val it = i.bytes.iterator
        while (it.hasNext) {
          builder += reader(it)
        }
        builder.result()
      }
    }
  }

  def imagePacker(image: Image): Either[String, IndexedSeq[Primitive[_]] => ImageElement] = {
    val singleComponent = image.components.map(_._2.componentType) match {
      case List(single) => single
      case _            => return Left("Can only convert single component images to tensors")
    }
    if (image.format != ImageFormat.Plain) {
      return Left("Only plain images can be converted")
    }
    for {
      writer <- buildWriter(singleComponent)
      bs <- byteSize(singleComponent)
    } yield { elements: IndexedSeq[Primitive[_]] =>
      {
        val builder = ByteString.newBuilder
        builder.sizeHint(image.width * image.height * bs)
        val it = elements.iterator
        while (it.hasNext) {
          writer(it.next(), builder)
        }
        ImageElement(builder.result())
      }
    }
  }

  private def buildReader(ft: FundamentalType): Either[String, ByteIterator => Primitive[_]] = {
    val reader: ByteIterator => Primitive[_] = ft match {
      case FundamentalType.Uint8 =>
        it => Primitive(it.getByte)
      case FundamentalType.Int8 =>
        it => Primitive(it.getByte)
      case FundamentalType.Int32 =>
        it => Primitive(it.getInt)
      case FundamentalType.Uint32 =>
        it => Primitive(it.getInt)
      case FundamentalType.Int64 =>
        it => Primitive(it.getLong)
      case FundamentalType.Uint64 =>
        it => Primitive(it.getLong)
      case FundamentalType.Float32 =>
        it => Primitive(it.getFloat)
      case FundamentalType.Float64 =>
        it => Primitive(it.getDouble)
      case other =>
        return Left(s"Type ${other} not supported")
    }
    Right(reader)
  }

  private def buildWriter(ft: FundamentalType): Either[String, (Primitive[_], ByteStringBuilder) => Unit] = {
    val writer: (Primitive[_], ByteStringBuilder) => Unit = ft match {
      case FundamentalType.Uint8 =>
        (a, b) => b.putByte(a.asInstanceOf[Primitive[Byte]].x)
      case FundamentalType.Int8 =>
        (a, b) => b.putByte(b.asInstanceOf[Primitive[Byte]].x)
      case FundamentalType.Int32 =>
        (a, b) => b.putInt(a.asInstanceOf[Primitive[Int]].x)
      case FundamentalType.Uint32 =>
        (a, b) => b.putInt(a.asInstanceOf[Primitive[Int]].x)
      case FundamentalType.Int64 =>
        (a, b) => b.putLong(a.asInstanceOf[Primitive[Long]].x)
      case FundamentalType.Uint64 =>
        (a, b) => b.putLong(a.asInstanceOf[Primitive[Long]].x)
      case FundamentalType.Float32 =>
        (a, b) => b.putFloat(a.asInstanceOf[Primitive[Float]].x)
      case FundamentalType.Float64 =>
        (a, b) => b.putDouble(a.asInstanceOf[Primitive[Double]].x)
      case _ =>
        return Left(s"Type ${ft} not supported")
    }
    Right(writer)
  }

  /** Returns the byte size of a component, encoded in an image. */
  private def byteSize(ft: FundamentalType): Either[String, Int] = {
    val size: Int = ft match {
      case FundamentalType.Uint8 | FundamentalType.Int8   => 1
      case FundamentalType.Uint32 | FundamentalType.Int32 => 4
      case FundamentalType.Uint64 | FundamentalType.Int64 => 8
      case FundamentalType.Float32                        => 4
      case FundamentalType.Float64                        => 4
      case _ =>
        return Left(s"Type ${ft} not supported")
    }
    Right(size)
  }

}

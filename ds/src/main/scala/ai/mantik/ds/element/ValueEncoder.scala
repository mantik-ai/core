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
package ai.mantik.ds.element

import ai.mantik.ds.{DataType, FundamentalType, Nullable}
import FundamentalType._

/**
  * Value encoder provides implicit conversion from Scala Types into Mantik Types
  * and can be used to construct primitives
  */
trait ValueEncoder[T] {
  def dataType: DataType

  def wrap(x: T): Element

  def unwrap(in: Element): T
}

object ValueEncoder {

  private def makePrimitiveValueEncoder[T, ST, FT <: FundamentalType](ft: FT, convert: T => ST, decode: ST => T) =
    new ValueEncoder[T] {
      def dataType = ft

      override def wrap(x: T): Element = Primitive(convert(x))

      override def unwrap(in: Element): T = decode(in.asInstanceOf[Primitive[ST]].x)
    }

  implicit val byteEncoder = makePrimitiveValueEncoder[Byte, Byte, Int8.type](Int8, identity, identity)
  implicit val shortEncoder = makePrimitiveValueEncoder[Short, Int, Int32.type](Int32, _.toInt, _.toShort)
  implicit val charEncoder = makePrimitiveValueEncoder[Char, Int, Int32.type](Int32, _.toInt, _.toChar)
  implicit val intEncoder = makePrimitiveValueEncoder[Int, Int, Int32.type](Int32, identity, identity)
  implicit val longEncoder = makePrimitiveValueEncoder[Long, Long, Int64.type](Int64, identity, identity)
  implicit val floatEncoder = makePrimitiveValueEncoder[Float, Float, Float32.type](Float32, identity, identity)
  implicit val doubleEncoder = makePrimitiveValueEncoder[Double, Double, Float64.type](Float64, identity, identity)
  implicit val boolEncoder = makePrimitiveValueEncoder[Boolean, Boolean, BoolType.type](BoolType, identity, identity)
  implicit val stringEncoder =
    makePrimitiveValueEncoder[String, String, StringType.type](StringType, identity, identity)
  implicit val unitEncoder = makePrimitiveValueEncoder[Unit, Unit, VoidType.type](VoidType, identity, identity)

  implicit def optionalEncoder[T](implicit subEncoder: ValueEncoder[T]): ValueEncoder[Option[T]] =
    new ValueEncoder[Option[T]] {
      override def dataType: DataType = Nullable(subEncoder.dataType)

      override def wrap(x: Option[T]): Element = x match {
        case None        => NullElement
        case Some(value) => SomeElement(subEncoder.wrap(value))
      }

      override def unwrap(in: Element): Option[T] = in match {
        case NullElement        => None
        case SomeElement(value) => Some(subEncoder.unwrap(value))
        case _                  => throw new IllegalArgumentException(s"Unexpected element ${in}")
      }
    }

  def wrap[T: ValueEncoder](x: T): Element = {
    implicitly[ValueEncoder[T]].wrap(x)
  }

  def apply[T: ValueEncoder](x: T): Element = wrap(x)
}

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

import io.circe.{Decoder, Encoder, ObjectEncoder}
import io.circe.syntax._

import scala.collection.immutable.ListMap

// Note: this is just a rough draft and subject of heavy changes.

// TODO: While ListMap preserve the order (which is good for us) they have bad performance characteristics (Ticket #38)!

/** Describes a single Datatype. */
sealed trait DataType {

  /** Returns a JSON String representation of the type. */
  def toJsonString: String = {
    (this: DataType).asJson(DataType.encoder).noSpaces
  }

  /** Returns true if this data type is nullable. */
  def isNullable: Boolean = this match {
    case Nullable(_) => true
    case _           => false
  }
}

object DataType {
  implicit lazy val encoder: Encoder[DataType] = DataTypeJsonAdapter.typeEncoder
  implicit lazy val decoder: Decoder[DataType] = DataTypeJsonAdapter.typeDecoder
}

/** Tabular data, usually the root of the data. */
case class TabularData(
    columns: ListMap[String, DataType]
) extends DataType {

  /** Returns the index of a column with a given name. */
  def lookupColumnIndex(name: String): Option[Int] = {
    val it = columns.iterator
    it.indexWhere(_._1 == name) match {
      case -1 => None
      case n  => Some(n)
    }
  }

  def columnByIndex(index: Int): Option[(String, DataType)] = {
    // Perhaps it's better to store Vectors at the first place #3
    val asVector = columns.toVector
    if (asVector.isDefinedAt(index)) {
      Some(asVector(index))
    } else {
      None
    }
  }

  override def toString: String = {
    val builder = StringBuilder.newBuilder
    var first = true
    builder ++= "TabularData("
    columns.foreach { case (name, value) =>
      if (!first) {
        builder ++= ","
      }
      builder ++= s"$name:${value.toString}"
      first = false
    }
    builder ++= ")"
    builder.result()
  }
}

object TabularData {

  /** Build a tabular data from a name type list. */
  def apply(columns: (String, DataType)*): TabularData = TabularData(ListMap(columns: _*))

  implicit val encoder: Encoder[TabularData] = DataTypeJsonAdapter.typeEncoder.contramap { x: TabularData =>
    x: DataType
  }
  implicit val decoder: Decoder[TabularData] = DataTypeJsonAdapter.typeDecoder.emap {
    case td: TabularData => Right(td)
    case other           => Left(s"Expected TabularData, got ${other.getClass.getSimpleName}")
  }
}

/** Describes a fundamental type. */
sealed trait FundamentalType extends DataType {

  /** Returns the simple string name of the fundamental type. */
  def name: String = DataTypeJsonAdapter.fundamentalTypeToName(this)

  override def toString: String = name
}

object FundamentalType {
  sealed trait IntegerType extends FundamentalType {
    def bits: Int
  }

  sealed abstract class SignedInteger(val bits: Int) extends IntegerType
  sealed abstract class UnsignedInteger(val bits: Int) extends IntegerType

  case object Int8 extends SignedInteger(8)
  case object Int32 extends SignedInteger(32)
  case object Int64 extends SignedInteger(64)
  case object Uint8 extends UnsignedInteger(8)
  case object Uint32 extends UnsignedInteger(32)
  case object Uint64 extends UnsignedInteger(64)

  sealed abstract class FloatingPoint(val bits: Int, val fraction: Int) extends FundamentalType
  case object Float32 extends FloatingPoint(32, 23)
  case object Float64 extends FloatingPoint(64, 52)

  case object StringType extends FundamentalType

  case object BoolType extends FundamentalType

  case object VoidType extends FundamentalType

  /** Parses a fundamental type from Name. */
  def fromName(name: String): Option[FundamentalType] = DataTypeJsonAdapter.fundamentalTypeFromName(name)
}

sealed trait ImageChannel {
  def name: String = DataTypeJsonAdapter.imageChannelName(this)
}

object ImageChannel {
  case object Red extends ImageChannel
  case object Blue extends ImageChannel
  case object Green extends ImageChannel
  case object Black extends ImageChannel

  /** Parses a channel from name. */
  def fromName(name: String): Option[ImageChannel] = DataTypeJsonAdapter.imageChannelFromName(name)
}

/** Describe a single image component */
case class ImageComponent(
    componentType: FundamentalType
)

sealed trait ImageFormat

object ImageFormat {

  /** Image is plain encoded (bytes directly after each other, row after row). */
  case object Plain extends ImageFormat

  /** Image is in PNG Format. */
  case object Png extends ImageFormat
}

/** DataType for images. */
case class Image(
    width: Int,
    height: Int,
    components: ListMap[ImageChannel, ImageComponent],
    format: ImageFormat = ImageFormat.Plain
) extends DataType {

  private def channelsToString: String = {
    components
      .map { case (channel, component) =>
        channel.toString + ": " + component.componentType.toString
      }
      .mkString(",")
  }

  override def toString: String = s"Image(${width}x${height}, [${channelsToString}])"
}

object Image {

  /** Convenience constructor for plain Images. */
  def plain(
      width: Int,
      height: Int,
      components: (ImageChannel, FundamentalType)*
  ): Image = {
    Image(
      width,
      height,
      ListMap(components.map { case (channel, dataType) =>
        channel -> ImageComponent(dataType)
      }: _*)
    )
  }
}

/**
  * Data Type for Tensors
  * @param componentType underlying fundamental type (dtype in TensorFlow).
  * @param shape shape of the Tensor. No support for varying shape yet.
  */
case class Tensor(
    componentType: FundamentalType,
    shape: Seq[Int]
) extends DataType {
  require(shape.nonEmpty, "Shape may not be empty")
  require(shape.forall(_ > 0), "All shape elements must be > 0")

  /** Returns the element count a packed tensor would have. */
  lazy val packedElementCount: Long = shape.foldLeft(1L)(_ * _.toLong)

  override def toString: String = {
    s"Tensor(${componentType.name}, [${shape.mkString(",")}])"
  }
}

/** A Nullable DataType. */
case class Nullable(
    underlying: DataType
) extends DataType {
  override def toString: String = s"nullable ${underlying}"
}

object Nullable {

  /**
    * If already nullable, returns the dataType, otherwise wrap it inside
    * a Nullable
    */
  def makeNullable(dataType: DataType): Nullable = {
    dataType match {
      case n: Nullable => n
      case otherwise   => Nullable(otherwise)
    }
  }

  /**
    * If in is nullable, call f on underlying and wrap in Nullable, otherwise
    * call f on in.
    * (Can be useful on functions which map values to nullable, if nullable given)
    */
  def mapIfNullable(in: DataType, f: DataType => DataType): DataType = {
    in match {
      case n: Nullable => Nullable.makeNullable(f(n.underlying))
      case otherwise   => f(otherwise)
    }
  }

  /** Unpack if it's a nullable. */
  def unwrap(in: DataType): DataType = {
    in match {
      case Nullable(underlying) => underlying
      case somethingElse        => somethingElse
    }
  }
}

/**
  * A Array type.
  * Note: called ArrayT to make it distinguishable with Scala's Array.
  */
case class ArrayT(
    underlying: DataType
) extends DataType {
  override def toString: String = s"Array(${underlying})"
}

/** A Tuple whose elements have names. */
case class Struct(
    fields: ListMap[String, DataType]
) extends DataType {

  /** Arity of tuple */
  def arity: Int = fields.size

  /** Returns the index of a field with a given name. */
  def lookupFieldIndex(name: String): Option[Int] = {
    val it = fields.iterator
    it.indexWhere(_._1 == name) match {
      case -1 => None
      case n  => Some(n)
    }
  }

  override def toString: String = {
    s"Struct" + fields
      .map { case (name, dt) =>
        s"${name}:${dt}"
      }
      .mkString("(", ",", ")")
  }
}

object Struct {

  /** Convenience constructor. */
  def apply(nameColumns: (String, DataType)*): Struct = {
    Struct(ListMap(nameColumns: _*))
  }
}

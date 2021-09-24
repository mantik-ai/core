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

import ai.mantik.ds.{DataType, FundamentalType}
import ai.mantik.ds.converter.Cast
import ai.mantik.ds.element.{Primitive, PrimitiveEncoder}

import scala.collection.mutable

private[converter] object FundamentalCasts {

  def findFundamentalCast(from: FundamentalType, to: FundamentalType): Either[String, Cast] = {
    if (from == to) {
      return Right(Cast(from, to, false, false, identity))
    }
    combinedCasts.get((from, to)) match {
      case None       => Left(s"No cast found from ${from} to ${to}")
      case Some(cast) => Right(cast)
    }
  }

  private lazy val combinedCasts: Map[(DataType, DataType), Cast] = buildFundamentalCastMap()

  private def buildFundamentalCastMap(): Map[(DataType, DataType), Cast] = {
    val builder = mutable.Map.empty[(DataType, DataType), List[Cast]]
    for (f <- fundamentalCasts) {
      builder += ((f.from, f.to) -> List(f))
    }

    def score(list: List[Cast]): Int = {
      val length = -list.length
      val isLoosingMinus = if (list.exists(_.loosing)) -100 else 0
      val canFailMinus = if (list.exists(_.canFail)) -200 else 0
      length + isLoosingMinus + canFailMinus
    }

    var added = true
    while (added) {
      val castAdd = for {
        f <- fundamentalCasts
        ((existingFrom, existingTo), casts) <- builder
        if f.to != FundamentalType.StringType // do not go via string
        if existingFrom == f.to
        if f.from != existingTo
        if builder.get((f.from, existingTo)).forall(x => score(x) < score(f :: casts))
      } yield {
        (f.from, existingTo) -> (f :: casts)
      }

      builder ++= castAdd
      added = castAdd.nonEmpty
    }

    val result = builder.view.mapValues {
      case List(single) => single
      case multiple =>
        multiple.reduce(_.append(_))
    }
    result.toMap
  }

  private val fundamentalCasts = Seq(
    makeCastOp(FundamentalType.Int8, FundamentalType.Int32, false, false)((x => x.toInt): Byte => Int),
    makeCastOp(FundamentalType.Int32, FundamentalType.Int64, false, false)((x => x.toLong): Int => Long),
    makeCastOp(FundamentalType.Int64, FundamentalType.Int32, true, false)((x => x.toInt): Long => Int),
    makeCastOp(FundamentalType.Int32, FundamentalType.Int8, true, false)((x => x.toByte): Int => Byte),
    makeCastOp(FundamentalType.Uint8, FundamentalType.Int8, true, false)(identity: Byte => Byte),
    makeCastOp(FundamentalType.Int8, FundamentalType.Uint8, true, false)(identity: Byte => Byte),
    makeCastOp(FundamentalType.Uint8, FundamentalType.Float32, loosing = false, canFail = false)(
      (x => java.lang.Byte.toUnsignedInt(x).toFloat): Byte => Float
    ),
    makeCastOp(FundamentalType.Int8, FundamentalType.Float32, loosing = false, canFail = false)(
      (x => x.toFloat): Byte => Float
    ),
    makeCastOp(FundamentalType.Uint8, FundamentalType.Int32, false, false)((x => x & 0xff): Byte => Int),
    makeCastOp(FundamentalType.Uint32, FundamentalType.Int64, false, false)((x => x & 0xffffffffL): Int => Long),
    makeCastOp(FundamentalType.Uint32, FundamentalType.Int32, true, false)(identity: Int => Int),
    makeCastOp(FundamentalType.Int32, FundamentalType.Uint32, true, false)(identity: Int => Int),
    makeCastOp(FundamentalType.Uint32, FundamentalType.Float64, loosing = false, canFail = false)(
      (x => java.lang.Integer.toUnsignedLong(x).toDouble): Int => Double
    ),
    makeCastOp(FundamentalType.Int32, FundamentalType.Float64, loosing = false, canFail = false)(
      (x => x.toDouble): Int => Double
    ),
    makeCastOp(FundamentalType.Uint64, FundamentalType.Int64, true, false)(identity: Long => Long),
    makeCastOp(FundamentalType.Int64, FundamentalType.Uint64, true, false)(identity: Long => Long),
    makeCastOp(FundamentalType.Int64, FundamentalType.Float32, true, false)((x => x.toFloat): Long => Float),
    makeCastOp(FundamentalType.Int64, FundamentalType.Float64, true, false)((x => x.toDouble): Long => Double),
    makeCastOp(FundamentalType.Float32, FundamentalType.Float64, false, false)((x => x.toDouble): Float => Double),
    makeCastOp(FundamentalType.Float64, FundamentalType.Float32, true, false)((x => x.toFloat): Double => Float),
    makeCastOp(FundamentalType.Float64, FundamentalType.Int64, loosing = true, canFail = false)(
      (x => x.toLong): Double => Long
    ),
    makeCastOp(FundamentalType.Float64, FundamentalType.Uint64, loosing = true, canFail = false)(
      (x => x.toLong): Double => Long
    ),
    makeCastOp(FundamentalType.Int32, FundamentalType.BoolType, true, false)((x => x != 0): Int => Boolean),
    makeCastOp(FundamentalType.BoolType, FundamentalType.Int32, false, false)((x => if (x) 1 else 0): Boolean => Int),
    makeStringParser(FundamentalType.Int8)(_.toByte),
    makeStringParser(FundamentalType.Int32)(_.toInt),
    makeStringParser(FundamentalType.Int64)(_.toLong),
    makeStringParser(FundamentalType.Float32)(_.toFloat),
    makeStringParser(FundamentalType.Float64)(_.toDouble),
    makeStringParser(FundamentalType.BoolType)(_.toBoolean),
    makeStringParser(FundamentalType.VoidType)(_ => ()),
    makeStringParser(FundamentalType.Uint8) { x =>
      val asInt = x.toInt
      if (asInt < 0 || asInt > 255) {
        throw new NumberFormatException(s"${asInt} is out of range")
      }
      asInt.toByte
    },
    makeStringParser(FundamentalType.Uint32) { x =>
      Integer.parseUnsignedInt(x)
    },
    makeStringParser(FundamentalType.Uint64) { x =>
      java.lang.Long.parseUnsignedLong(x)
    },
    makeStringSerializer(FundamentalType.BoolType)((_.toString): Boolean => String),
    makeStringSerializer(FundamentalType.Int8)((_.toString): Byte => String),
    makeStringSerializer(FundamentalType.Int32)((_.toString): Int => String),
    makeStringSerializer(FundamentalType.Int64)((_.toString): Long => String),
    makeStringSerializer(FundamentalType.Float32)((_.toString): Float => String),
    makeStringSerializer(FundamentalType.Float64)((_.toString): Double => String),
    makeStringSerializer(FundamentalType.VoidType)((_ => "void"): Unit => String),
    makeStringSerializer(FundamentalType.Uint8)((x => java.lang.Byte.toUnsignedInt(x).toString): Byte => String),
    makeStringSerializer(FundamentalType.Uint32)((x => java.lang.Integer.toUnsignedString(x)): Int => String),
    makeStringSerializer(FundamentalType.Uint64)((x => java.lang.Long.toUnsignedString(x)): Long => String)
  )

  /** Creates a cast operation. The implicit Aux-Pattern is used solely for checking that Types are matching. */
  private def makeCastOp[From <: FundamentalType, To <: FundamentalType, FromS, ToS](
      from: From,
      to: To,
      loosing: Boolean,
      canFail: Boolean
  )(f: FromS => ToS)(
      implicit fromAux: PrimitiveEncoder.Aux[From, FromS],
      toAux: PrimitiveEncoder.Aux[To, ToS]
  ): Cast = {
    Cast(from, to, loosing, canFail, e => Primitive(f(e.asInstanceOf[Primitive[FromS]].x)))
  }

  /** Creates a cast operation from String to a type. */
  private def makeStringParser[To <: FundamentalType, ST](to: To)(f: String => ST)(
      implicit aux: PrimitiveEncoder.Aux[To, ST]
  ): Cast = {
    Cast(FundamentalType.StringType, to, false, true, e => Primitive(f(e.asInstanceOf[Primitive[String]].x)))
  }

  private def makeStringSerializer[From <: FundamentalType, ST](from: From)(f: ST => String)(
      implicit aux: PrimitiveEncoder.Aux[From, ST]
  ): Cast = {
    Cast(from, FundamentalType.StringType, false, false, e => Primitive(f(e.asInstanceOf[Primitive[ST]].x)))
  }
}

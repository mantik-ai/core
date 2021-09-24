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
package ai.mantik.ds.sql.run

import ai.mantik.ds.DataType
import ai.mantik.ds.element.SingleElementBundle
import io.circe.Decoder.Result
import io.circe.generic.semiauto
import io.circe.syntax._
import io.circe._

import scala.annotation.tailrec

/** Json Support for [[Program]] and [[OpCode]]. */
object ProgramJson {

  /** Translates many opcodes to json. */
  implicit val opCodesEncoder: Encoder.AsArray[Vector[OpCode]] = new Encoder.AsArray[Vector[OpCode]] {
    override def encodeArray(a: Vector[OpCode]): Vector[Json] = {
      a.flatMap(opCodeToJson)
    }
  }

  /** Parses many obcodes from array list. */
  implicit val opCodesDecoder: Decoder[Vector[OpCode]] = new Decoder[Vector[OpCode]] {
    override def apply(c: HCursor): Result[Vector[OpCode]] = {
      c.values match {
        case None         => Left(DecodingFailure("Expected opcode array", c.history))
        case Some(values) => consume(values.toList, Nil).map(_.toVector)
      }
    }
  }

  /** Encoder for Programs. */
  implicit val programEncoder: Encoder.AsObject[Program] = semiauto.deriveEncoder[Program]

  /** Decoder for Programs */
  implicit val programDecoder: Decoder[Program] = semiauto.deriveDecoder[Program]

  @tailrec
  private def consume(list: List[Json], pending: List[OpCode] = Nil): Result[List[OpCode]] = {
    list match {
      case Nil => Right(pending.reverse)
      case elements =>
        consumeOpCode(list) match {
          case Left(error) => Left(error)
          case Right((code, rest)) =>
            consume(rest, code :: pending)
        }
    }
  }

  /** Translates an opcode into JSON Elements, which can be concateneated. */
  def opCodeToJson(opCode: OpCode): List[Json] = {
    val head = opCode.code.asJson
    val extra: List[Json] = opCode match {
      case OpCode.Get(id)                          => List(id.asJson)
      case OpCode.Constant(value)                  => List(value.asJson)
      case OpCode.Pop                              => Nil
      case OpCode.Cast(from, to)                   => List(from.asJson, to.asJson)
      case OpCode.Neg                              => Nil
      case OpCode.Equals(dataType)                 => List(dataType.asJson)
      case OpCode.And                              => Nil
      case OpCode.Or                               => Nil
      case OpCode.ReturnOnFalse                    => Nil
      case OpCode.BinaryOp(dt, op)                 => List(dt.asJson, op.asJson)
      case OpCode.IsNull                           => Nil
      case OpCode.UnpackNullableJump(offset, drop) => List(offset.asJson, drop.asJson)
      case OpCode.PackNullable                     => Nil
      case OpCode.ArrayGet                         => Nil
      case OpCode.ArraySize                        => Nil
      case OpCode.StructGet(idx)                   => List(idx.asJson)
    }
    head :: extra
  }

  /** Consume an opcode from a serialized opcode list. */
  def consumeOpCode(list: List[Json]): Result[(OpCode, List[Json])] = {
    val (code, rest) = list.headOption.flatMap(_.asString) match {
      case None       => return Left(DecodingFailure("Empty list", Nil))
      case Some(code) => (code, list.tail)
    }

    def get0(code: OpCode): Result[(OpCode, List[Json])] = Right(code, rest)

    def get1[T: Decoder](f: T => OpCode): Result[(OpCode, List[Json])] = {
      rest match {
        case Nil => Left(DecodingFailure("Expected one extra argument", Nil))
        case head :: tail =>
          head.as[T].map(a => f(a) -> tail)
      }
    }

    def get2[A: Decoder, B: Decoder](f: (A, B) => OpCode): Result[(OpCode, List[Json])] = {
      rest match {
        case a :: b :: tail =>
          for {
            aDecoded <- a.as[A]
            bDecoded <- b.as[B]
          } yield {
            f(aDecoded, bDecoded) -> tail
          }
        case other =>
          Left(DecodingFailure("Expected two arguments", Nil))
      }
    }

    code match {
      case OpCode.GetCode                => get1[Int](OpCode.Get)
      case OpCode.ConstantCode           => get1[SingleElementBundle](OpCode.Constant)
      case OpCode.PopCode                => get0(OpCode.Pop)
      case OpCode.CastCode               => get2[DataType, DataType](OpCode.Cast)
      case OpCode.NegCode                => get0(OpCode.Neg)
      case OpCode.EqualsCode             => get1[DataType](OpCode.Equals)
      case OpCode.AndCode                => get0(OpCode.And)
      case OpCode.OrCode                 => get0(OpCode.Or)
      case OpCode.ReturnOnFalseCode      => get0(OpCode.ReturnOnFalse)
      case OpCode.BinaryOpCode           => get2(OpCode.BinaryOp)
      case OpCode.IsNullCode             => get0(OpCode.IsNull)
      case OpCode.UnpackNullableJumpCode => get2(OpCode.UnpackNullableJump)
      case OpCode.PackNullableCode       => get0(OpCode.PackNullable)
      case OpCode.ArrayGetCode           => get0(OpCode.ArrayGet)
      case OpCode.ArraySizeCode          => get0(OpCode.ArraySize)
      case OpCode.StructGetCode          => get1(OpCode.StructGet)
      case other =>
        Left(DecodingFailure(s"unknown op code ${other}", Nil))
    }
  }

}

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
package ai.mantik.ds.functional

import ai.mantik.ds.Errors.DataTypeMismatchException
import ai.mantik.ds.{DataType, TabularData}
import ai.mantik.ds.converter.Cast
import ai.mantik.ds.element.{Element, Primitive, TabularRow, ValueEncoder}
import ai.mantik.ds.functional.FunctionConverter.{InputDecoder, OutputEncoder}
import shapeless.{::, Generic, HList, HNil}

/**
  * Type class, which helps converting a Scala Function into a function
  * working on encoded elements.
  *
  * The generation is done using Shapeless type class magic.
  */
trait FunctionConverter[I, O] {

  /** Creates a Decoder for Table Rows, so that each Row represents I */
  @throws[DataTypeMismatchException]("If the data can't be mapped into given input data")
  def buildDecoderForTables(in: TabularData): TabularRow => I

  /** Returns the resulting table type, if each row represents O */
  def destinationTypeAsTable(): TabularData

  /** Returns an encoder which encode O into a table row */
  def buildEncoderForTables(): O => TabularRow

}

object FunctionConverter {

  /** Type class which creates an input decoder for a given I
    * Used to generate [[FunctionConverter]]
    */
  trait InputDecoder[I] {
    def buildDecoder(in: IndexedSeq[DataType]): IndexedSeq[Element] => I

    def arity: Int
  }

  /** Build an input decoder for primitive values. */
  implicit def primitiveDecoder[T](implicit e: ValueEncoder[T]): InputDecoder[T] = new InputDecoder[T] {
    override def buildDecoder(inputData: IndexedSeq[DataType]): IndexedSeq[Element] => T = {
      val inputType = if (inputData.isEmpty) {
        throw new DataTypeMismatchException(s"Input data has not enough columns")
      } else {
        inputData.head
      }
      val cast = Cast.findCast(inputType, e.dataType).getOrElse {
        throw new DataTypeMismatchException(s"Could not cast from ${inputType} to ${e.dataType}")
      }
      val castOp = cast.op
      in => e.unwrap(castOp(in.head))
    }

    override def arity: Int = 1
  }

  implicit val hnilDecoder: InputDecoder[HNil] = new InputDecoder[HNil] {
    override def buildDecoder(in: IndexedSeq[DataType]): IndexedSeq[Element] => HNil = { _ =>
      HNil
    }

    override def arity: Int = 0
  }

  implicit def hlistDecoder[H, T <: HList](implicit hd: InputDecoder[H], td: InputDecoder[T]): InputDecoder[H :: T] =
    new InputDecoder[H :: T] {
      override def buildDecoder(in: IndexedSeq[DataType]): IndexedSeq[Element] => H :: T = {
        val cut = hd.arity
        val left = in.take(cut)
        val right = in.drop(cut)
        val leftDecoder = hd.buildDecoder(left)
        val rightDecoder = td.buildDecoder(right)
        input => {
          leftDecoder(input.take(cut)) :: rightDecoder(input.drop(cut))
        }
      }

      override def arity: Int = hd.arity + td.arity
    }

  implicit def genericDecoder[T, H](implicit g: Generic.Aux[T, H], d: InputDecoder[H]): InputDecoder[T] =
    new InputDecoder[T] {
      override def buildDecoder(in: IndexedSeq[DataType]): IndexedSeq[Element] => T = {
        val decoder = d.buildDecoder(in)
        elements => g.from(decoder(elements))
      }

      override def arity: Int = {
        d.arity
      }
    }

  /** Type class which creates an output encoder for O
    * Used to generate [[FunctionConverter]]
    */
  trait OutputEncoder[O] {
    def buildEncoder(): O => IndexedSeq[Element]

    def destinationType(): IndexedSeq[DataType]
  }

  /** Build an output encoder for primitive types. */
  implicit def primitiveEncoder[T](implicit e: ValueEncoder[T]): OutputEncoder[T] = new OutputEncoder[T] {
    override def buildEncoder(): T => IndexedSeq[Element] = { v =>
      IndexedSeq(e.wrap(v))
    }

    override def destinationType(): IndexedSeq[DataType] = {
      IndexedSeq(e.dataType)
    }
  }

  implicit val hnilEncoder: OutputEncoder[HNil] = new OutputEncoder[HNil] {
    override def buildEncoder(): HNil => IndexedSeq[Element] = _ => IndexedSeq.empty

    override def destinationType(): IndexedSeq[DataType] = IndexedSeq.empty
  }

  implicit def hlistEncoder[H, T <: HList](implicit he: OutputEncoder[H], te: OutputEncoder[T]): OutputEncoder[H :: T] =
    new OutputEncoder[H :: T] {
      override def buildEncoder(): H :: T => IndexedSeq[Element] = {
        val hEncoder = he.buildEncoder()
        val tEncoder = te.buildEncoder()

        value => {
          hEncoder(value.head) ++ tEncoder(value.tail)
        }
      }

      override def destinationType(): IndexedSeq[DataType] = {
        he.destinationType() ++ te.destinationType()
      }
    }

  implicit def genericEncoder[T, H](implicit g: Generic.Aux[T, H], d: OutputEncoder[H]): OutputEncoder[T] =
    new OutputEncoder[T] {
      override def buildEncoder(): T => IndexedSeq[Element] = {
        val embedded = d.buildEncoder()
        value => {
          embedded(g.to(value))
        }
      }

      override def destinationType(): IndexedSeq[DataType] = {
        d.destinationType()
      }
    }

  implicit def makeFunctionConverter[I, O](
      implicit i: InputDecoder[I],
      o: OutputEncoder[O]
  ): FunctionConverter[I, O] = {
    new FunctionConverter[I, O] {

      final def buildDecoderForTables(in: TabularData): TabularRow => I = {
        val sub = i.buildDecoder(in.columns.map(_._2).toIndexedSeq)
        row => {
          sub(row.columns)
        }
      }

      final def buildEncoderForTables(): O => TabularRow = {
        val encoder = o.buildEncoder()
        encoder.andThen(x => TabularRow(x))
      }

      final def destinationTypeAsTable(): TabularData = {
        val subTypes = o.destinationType()
        TabularData(subTypes.zipWithIndex.map { case (dt, idx) =>
          s"_${idx + 1}" -> dt
        }: _*)
      }
    }
  }

}

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

import ai.mantik.ds.{DataType, FundamentalType, Image, ArrayT, Struct, Nullable, TabularData, Tensor}

import Ordering.Implicits._

object ElementOrdering {

  /** Returns an ordering for a DataType's Elements */
  def elementOrdering(dataType: DataType): Ordering[Element] = {
    dataType match {
      case ft: FundamentalType => castToElement(fundamentalOrdering(PrimitiveEncoder.lookup(ft)))
      case td: TabularData     => castToElement(embeddedTabularOrdering(td))
      case id: Image           => castToElement(imageOrdering(id))
      case td: Tensor          => castToElement(tensorOrdering(td))
      case na: Nullable        => castToElement(nullableOrdering(na))
      case at: ArrayT          => castToElement(arrayOrdering(at))
      case st: Struct          => castToElement(structOrdering(st))
    }
  }

  private def castToElement[T <: Element](ordering: Ordering[T]): Ordering[Element] = {
    ordering.on(_.asInstanceOf[T])
  }

  /** Returns an ordering for rows matching given TabularData's rows. */
  def tableRowOrdering(tabularData: TabularData): Ordering[TabularRow] = {
    tupleOrdering(tabularData.columns.values).on(_.columns)
  }

  def tupleOrdering(dataTypes: Iterable[DataType]): Ordering[IndexedSeq[Element]] = {
    val subOrderings = dataTypes.map {
      elementOrdering
    }.toVector
    new Ordering[IndexedSeq[Element]] {
      override def compare(x: IndexedSeq[Element], y: IndexedSeq[Element]): Int = {
        elementWiseCompare(x, y, subOrderings)
      }
    }
  }

  private def elementWiseCompare[T](left: Iterable[T], right: Iterable[T], subOrderings: Iterable[Ordering[T]]): Int = {
    val lit = left.iterator
    val rit = right.iterator
    val sit = subOrderings.iterator
    while (lit.hasNext && rit.hasNext && sit.hasNext) {
      val so = sit.next()
      val subResult = so.compare(lit.next(), rit.next())
      if (subResult != 0) {
        return subResult
      }
    }
    // left, right and subOrderings must have the same length
    0
  }

  private def embeddedTabularOrdering(tabularData: TabularData): Ordering[EmbeddedTabularElement] = {
    implicit val rowOrdering = tableRowOrdering(tabularData)
    val seqOrdering = implicitly[Ordering[IndexedSeq[TabularRow]]]
    seqOrdering.on[EmbeddedTabularElement] { x =>
      x.rows
    }
  }

  private def imageOrdering(image: Image): Ordering[ImageElement] = {
    val bsOrdering: Ordering[IndexedSeq[Byte]] = implicitly
    Ordering.fromLessThan { case (left, right) =>
      val leftBytes = left.bytes
      val rightBytes = right.bytes
      bsOrdering.lt(leftBytes, rightBytes)
    }
  }

  private def tensorOrdering(tensor: Tensor): Ordering[TensorElement[_]] = {
    val pe = PrimitiveEncoder.lookup(tensor.componentType)
    implicit val elementOrdering: Ordering[pe.ScalaType] = pe.ordering
    val indexedSeqOrdering: Ordering[IndexedSeq[pe.ScalaType]] = implicitly[Ordering[IndexedSeq[pe.ScalaType]]]
    Ordering.fromLessThan { case (left, right) =>
      val leftTensorElement = left.asInstanceOf[TensorElement[pe.ScalaType]]
      val rightTensorElement = right.asInstanceOf[TensorElement[pe.ScalaType]]
      indexedSeqOrdering.lt(leftTensorElement.elements, rightTensorElement.elements)
    }
  }

  private def nullableOrdering(nullable: Nullable): Ordering[NullableElement] = {
    val underlying = elementOrdering(nullable.underlying)
    Ordering.fromLessThan {
      case (NullElement, NullElement) => false
      case (NullElement, _)           => true
      case (_, NullElement)           => false
      case (SomeElement(a), SomeElement(b)) =>
        underlying.lt(a, b)
    }
  }

  private def arrayOrdering(list: ArrayT): Ordering[ArrayElement] = {
    implicit val underlying = elementOrdering(list.underlying)
    val indexedSeqOrdering: Ordering[IndexedSeq[Element]] = implicitly
    indexedSeqOrdering.on(_.elements)
  }

  private def structOrdering(namedTuple: Struct): Ordering[StructElement] = {
    tupleOrdering(namedTuple.fields.values).on(_.elements)
  }

  private def fundamentalOrdering[_](primitiveEncoder: PrimitiveEncoder[_]): Ordering[Primitive[_]] = {
    val po = primitiveEncoder.ordering
    Ordering.fromLessThan { case (l, r) =>
      po.lt(l.x.asInstanceOf[primitiveEncoder.ScalaType], r.x.asInstanceOf[primitiveEncoder.ScalaType])
    }
  }
}

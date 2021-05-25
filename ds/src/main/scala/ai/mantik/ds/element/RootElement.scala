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

import akka.util.ByteString

/** Base trait for all encoded main elements. */
sealed trait Element

/** Base trait for main-transportation elements, can contain either [[SingleElement]] or [[TabularRow]]. */
sealed trait RootElement

/** A tabular row, a root element. */
case class TabularRow(
    columns: IndexedSeq[Element]
) extends RootElement

/** A Single element, for non-tabular data. */
case class SingleElement(
    element: Element
) extends RootElement

object TabularRow {

  /** Generate a new tabular row. */
  def apply(wrappers: ElementWrapper*): TabularRow = {
    TabularRow(wrappers.map(_.element).toIndexedSeq)
  }
}

/**
  * A Single Primitive Element, must be the matching Java Type
  * Note: unsigned are handled using their signed counterparts.
  * TODO: Discuss if this is ok.
  */
case class Primitive[@specialized(Int, Long, Boolean, Float, Double) X](x: X) extends Element

object Primitive {

  /** The empty value for VoidType. */
  val unit: Primitive[Unit] = Primitive[Unit](())
}

/** Single serialized image (big endian, flat) */
case class ImageElement(bytes: ByteString) extends Element

/**
  * Single serialized tensor element.
  * Serializing works from doing inner elements first, e.g.
  * {{{
  *   Shape = [4,2]
  *   [[0,1], [2,3], [4,5], [6,7]]
  * }}}
  *
  * @param elements inner elements.
  *
  * TODO: IndexedSeq is not specialized, so not memory efficient, see Ticket #44
  */
case class TensorElement[X](elements: IndexedSeq[X]) extends Element

/** Embedded tabular (hypothetical). */
case class EmbeddedTabularElement(rows: IndexedSeq[TabularRow]) extends Element

object EmbeddedTabularElement {
  def apply(rows: TabularRow*): EmbeddedTabularElement = EmbeddedTabularElement(rows.toIndexedSeq)
}

/** A Nullable Element (either present or not). */
sealed trait NullableElement extends Element

case class SomeElement(x: Element) extends NullableElement

object SomeElement {
  def apply(e: ElementWrapper): SomeElement = SomeElement(e.element)
}

case object NullElement extends NullableElement

/** Element of [[ai.mantik.ds.ArrayT]] */
case class ArrayElement(elements: IndexedSeq[Element]) extends Element

object ArrayElement {

  /** Convenience Constructor. */
  def apply(elements: Element*): ArrayElement = ArrayElement(elements.toIndexedSeq)
}

/** Named tuple element. */
case class StructElement(elements: IndexedSeq[Element]) extends Element

object StructElement {

  /** Convenience Constructor. */
  def apply(elements: Element*): StructElement = StructElement(elements.toIndexedSeq)
}

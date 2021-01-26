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
  def apply(elements: Element*): TabularRow = TabularRow(elements.toIndexedSeq)
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
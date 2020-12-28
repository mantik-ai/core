package ai.mantik.ds.element

import ai.mantik.ds.{ DataType, FundamentalType, Image, Nullable, TabularData, Tensor }

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
    }
  }

  private def castToElement[T <: Element](ordering: Ordering[T]): Ordering[Element] = {
    ordering.on(_.asInstanceOf[T])
  }

  /** Returns an ordering for rows matching given TabularData's rows. */
  def tableRowOrdering(tabularData: TabularData): Ordering[TabularRow] = {
    val subOrderings = tabularData.columns.map {
      case (_, dataType) =>
        elementOrdering(dataType)
    }.toVector
    new Ordering[TabularRow] {
      override def compare(x: TabularRow, y: TabularRow): Int = {
        elementWiseCompare(x.columns, y.columns, subOrderings)
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
    Ordering.fromLessThan {
      case (left, right) =>
        val leftBytes = left.bytes
        val rightBytes = right.bytes
        bsOrdering.lt(leftBytes, rightBytes)
    }
  }

  private def tensorOrdering(tensor: Tensor): Ordering[TensorElement[_]] = {
    val pe = PrimitiveEncoder.lookup(tensor.componentType)
    implicit val elementOrdering: Ordering[pe.ScalaType] = pe.ordering
    val indexedSeqOrdering: Ordering[IndexedSeq[pe.ScalaType]] = implicitly[Ordering[IndexedSeq[pe.ScalaType]]]
    Ordering.fromLessThan {
      case (left, right) =>
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

  private def fundamentalOrdering[_](primitiveEncoder: PrimitiveEncoder[_]): Ordering[Primitive[_]] = {
    val po = primitiveEncoder.ordering
    Ordering.fromLessThan {
      case (l, r) =>
        po.lt(l.x.asInstanceOf[primitiveEncoder.ScalaType], r.x.asInstanceOf[primitiveEncoder.ScalaType])
    }
  }
}

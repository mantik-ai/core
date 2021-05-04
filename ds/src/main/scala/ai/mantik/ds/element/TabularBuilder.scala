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

import ai.mantik.ds.{FundamentalType, Image, ArrayT, Struct, Nullable, TabularData, Tensor}

/** Builder for tabular data */
class TabularBuilder(tabularData: TabularData) {
  private val rowBuilder = Vector.newBuilder[TabularRow]

  /** Add a row (just use the pure Scala Types, no Primitives or similar. */
  def row(values: Any*): TabularBuilder = {
    addCheckedRow(values)
    this
  }

  def result: TabularBundle = TabularBundle(
    tabularData,
    rowBuilder.result()
  )

  private def addCheckedRow(values: Seq[Any]): Unit = {
    require(values.length == tabularData.columns.size)
    val converted = values.zip(tabularData.columns).map {
      case (value: Primitive[_], (columnName, pt: FundamentalType)) =>
        value
      case (value, (columnName, pt: FundamentalType)) =>
        val encoder = PrimitiveEncoder.lookup(pt)
        require(encoder.convert.isDefinedAt(value), s"Value  ${value} of class ${value.getClass} must fit to ${pt}")
        encoder.wrap(encoder.convert(value))
      case (value, (columnName, i: Image)) =>
        require(value.isInstanceOf[ImageElement])
        value.asInstanceOf[ImageElement]
      case (value: EmbeddedTabularElement, (columnName, d: TabularData)) =>
        value
      case (value, (columnName, i: Tensor)) =>
        require(value.isInstanceOf[TensorElement[_]])
        value.asInstanceOf[Element]
      case (value, (columnName, n: Nullable)) =>
        value match {
          case NullElement    => NullElement
          case None           => NullElement
          case s: SomeElement => s
          case v if n.underlying.isInstanceOf[FundamentalType] =>
            val encoder = PrimitiveEncoder.lookup(n.underlying.asInstanceOf[FundamentalType])
            require(encoder.convert.isDefinedAt(v), s"Value ${value} of class ${value.getClass} must fit into ${n}")
            SomeElement(encoder.wrap(encoder.convert(v)))
          case unsupported =>
            throw new IllegalArgumentException(s"Unsupported value ${unsupported} for ${n}")
        }
      case (value: ArrayElement, (columnName, l: ArrayT)) =>
        // TODO: More checks
        value
      case (value: StructElement, (columnName, nt: Struct)) =>
        // TODO: More checks
        value
      case (other, (columnName, dataType)) =>
        throw new IllegalArgumentException(s"Could not encode ${other} as ${dataType}")
    }
    rowBuilder += TabularRow(converted.toVector)
  }

}

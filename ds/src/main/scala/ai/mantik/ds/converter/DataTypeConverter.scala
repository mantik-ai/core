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
package ai.mantik.ds.converter

import ai.mantik.ds.{DataType, FundamentalType, TabularData}
import ai.mantik.ds.element._

/** A Converter for data types. */
trait DataTypeConverter {
  def targetType: DataType

  def convert(element: Element): Element
}

object DataTypeConverter {

  /** A Converter which does not convert anything. */
  case class IdentityConverter(dataType: DataType) extends DataTypeConverter {
    override def targetType: DataType = dataType

    override def convert(element: Element): Element = element
  }

  /** A Converter which just emits constants. */
  case class ConstantConverter(dataType: DataType, constant: Element) extends DataTypeConverter {
    override def targetType: DataType = dataType

    override def convert(element: Element): Element = constant
  }

  case class FunctionalConverter(dataType: DataType, f: Element => Element) extends DataTypeConverter {
    override def targetType: DataType = dataType

    override def convert(element: Element): Element = f(element)
  }

  /** Generates a converter which is is working within a functional type and converting to the same again. */
  def fundamental[T <: FundamentalType, ST](
      dt: T
  )(f: ST => ST)(implicit x: PrimitiveEncoder.Aux[T, ST]): DataTypeConverter = {
    FunctionalConverter(
      dt,
      p => Primitive(f(p.asInstanceOf[Primitive[ST]].x))
    )
  }
}

/** A Converter for root elements. */
trait RootElementConverter extends DataTypeConverter {

  /** Convert a root element. */
  def convert(rootElement: RootElement): RootElement
}

/** Converter for tabular data. */
case class TabularConverter(
    out: TabularData,
    elementConverters: IndexedSeq[DataTypeConverter]
) extends RootElementConverter {
  require(
    out.columns.values.toVector == elementConverters.map(_.targetType),
    s"Target type of element converters must match, expected: ${out.columns.values.toVector}, got ${elementConverters.map(_.targetType).toVector}"
  )

  override def targetType: DataType = out

  override def convert(rootElement: RootElement): RootElement = {
    val row = rootElement.asInstanceOf[TabularRow]
    convertRow(row)
  }

  override def convert(element: Element): Element = {
    val embeddedElement = element.asInstanceOf[EmbeddedTabularElement]
    EmbeddedTabularElement(
      embeddedElement.rows.map(convertRow)
    )
  }

  private def convertRow(tabularRow: TabularRow): TabularRow = {
    val count = tabularRow.columns.size
    require(count == elementConverters.size)

    val resultBuilder = Vector.newBuilder[Element]
    resultBuilder.sizeHint(count)

    var i = 0
    while (i < count) {
      resultBuilder += elementConverters(i).convert(tabularRow.columns(i))
      i += 1
    }
    TabularRow(resultBuilder.result())
  }
}

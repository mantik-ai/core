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

import ai.mantik.ds.{DataType, TabularData}

/** A Builder for [[ai.mantik.ds.element.TabularBundle]] which builds column wise. */
case class ColumnWiseBundleBuilder(private val columnsRev: List[(String, DataType, IndexedSeq[Element])] = Nil) {

  def result: TabularBundle = {
    val columns = columnsRev.reverse
    val rowCount = columns.head._3.length

    val tabularDataType = TabularData(columns.map { case (columnName, dataType, _) =>
      columnName -> dataType
    }: _*)

    val rows = for {
      i <- 0 until rowCount
    } yield {
      TabularRow(columns.map(_._3(i)).toVector)
    }

    TabularBundle(
      tabularDataType,
      rows.toVector
    )
  }

  /** Add a new column. */
  def withColumn(name: String, dataType: DataType, values: IndexedSeq[Element]): ColumnWiseBundleBuilder = {
    if (columnsRev.nonEmpty) {
      val rowCount = columnsRev.head._3.length
      require(values.length == rowCount, "Row count must be equal for all columns")
    }
    copy(
      columnsRev = (name, dataType, values) :: columnsRev
    )
  }

  /** Add a new column, filled with automatically detected primitive values. */
  def withPrimitives[T: ValueEncoder](name: String, values: T*): ColumnWiseBundleBuilder = {
    val encoder = implicitly[ValueEncoder[T]]
    withColumn(name, encoder.dataType, values.map(encoder.wrap).toIndexedSeq)
  }
}

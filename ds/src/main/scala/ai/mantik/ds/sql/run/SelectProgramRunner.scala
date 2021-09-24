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

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.TabularData
import ai.mantik.ds.element.{Bundle, Primitive, TabularBundle, TabularRow}
import ai.mantik.ds.sql.Select
import ai.mantik.ds.sql.run.SingleTableGeneratorProgramRunner.RowIterator

/**
  * Runs Select Programs
  */
class SelectProgramRunner @throws[FeatureNotSupported]("if some select feature could not be translated.") (
    selectProgram: SelectProgram
) {

  private val selectorRunner = selectProgram.selector.map(new ProgramRunner(_))
  private val projectionRunner = selectProgram.projector.map(new ProgramRunner(_))

  /**
    * Run the select statement.
    * Note: if the bundles data type doesn't match, behaviour is undefined.
    */
  def run(rows: RowIterator): RowIterator = {
    val newRows = rows.flatMap { row =>
      if (isSelected(row)) {
        Some(project(row))
      } else {
        None
      }
    }

    newRows
  }

  private def isSelected(row: TabularRow): Boolean = {
    selectorRunner match {
      case None => true
      case Some(runner) =>
        val selectResult = runner.run(row.columns)
        selectResult.head.asInstanceOf[Primitive[Boolean]].x
    }
  }

  private def project(row: TabularRow): TabularRow = {
    projectionRunner match {
      case None => row
      case Some(runner) =>
        TabularRow(runner.run(row.columns))
    }
  }

}

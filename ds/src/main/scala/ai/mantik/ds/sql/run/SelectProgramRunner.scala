package ai.mantik.ds.sql.run

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.TabularData
import ai.mantik.ds.element.{Bundle, Primitive, TabularBundle, TabularRow}
import ai.mantik.ds.sql.Select
import ai.mantik.ds.sql.run.SingleTableGeneratorProgramRunner.RowIterator

/**
  * Runs Select Programs
  *
  * @throws FeatureNotSupported if some select feature could not be translated.
  */
class SelectProgramRunner(selectProgram: SelectProgram) {

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

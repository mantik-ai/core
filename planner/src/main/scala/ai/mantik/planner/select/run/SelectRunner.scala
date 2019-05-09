package ai.mantik.planner.select.run

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.element.{ Bundle, Primitive, TabularBundle, TabularRow }
import ai.mantik.planner.select.Select

/**
 * Runs select statements on Bundles.
 *
 * @throws FeatureNotSupported if some select feature could not be translated.
 */
class SelectRunner(select: Select) {

  private val compiled = Compiler.compile(select) match {
    case Left(error)           => throw new FeatureNotSupported(error)
    case Right(compiledSelect) => compiledSelect
  }
  private val selectorRunner = compiled.selector.map(new Runner(_))
  private val projectionRunner = compiled.projector.map(new Runner(_))

  /**
   * Run the select statement.
   * Note: if the bundles data type doesn't match, behaviour is undefined.
   */
  def run(bundle: Bundle): Bundle = {
    val tabularBundle = bundle match {
      case t: TabularBundle => t
      case other =>
        throw new IllegalArgumentException("Bundle is incosistent, excepeted tabular rows")
    }
    val newRows = tabularBundle.rows.flatMap {
      case row: TabularRow =>
        if (isSelected(row)) {
          Some(project(row))
        } else {
          None
        }
      case other =>
        throw new IllegalArgumentException("Bundle is incosistent, excepeted tabular rows")
    }

    Bundle(
      select.resultingType,
      newRows
    )
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

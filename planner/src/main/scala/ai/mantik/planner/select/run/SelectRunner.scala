package ai.mantik.planner.select.run

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.element.{ Bundle, Primitive, TabularRow }
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
  private val selectorRunner = new Runner(compiled.selector)
  private val projectionRunner = new Runner(compiled.projector)

  /**
   * Run the select statement.
   * Note: if the bundles data type doesn't match, behaviour is undefined.
   */
  def run(bundle: Bundle): Bundle = {
    val newRows = bundle.rows.flatMap {
      case row: TabularRow =>
        val selectResult = selectorRunner.run(row.columns)
        if (!selectResult.head.asInstanceOf[Primitive[Boolean]].x) {
          // filter out
          None
        } else {
          Some(TabularRow(projectionRunner.run(row.columns)))
        }
      case other =>
        throw new IllegalArgumentException("Bundle is incosistent, excepeted tabular rows")
    }

    Bundle(
      select.resultingType,
      newRows
    )
  }

}

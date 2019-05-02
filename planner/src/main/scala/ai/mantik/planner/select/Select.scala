package ai.mantik.planner.select

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.TabularData
import ai.mantik.ds.element.Bundle
import ai.mantik.planner.select.builder.SelectBuilder
import ai.mantik.planner.select.run.SelectRunner

import scala.collection.immutable.ListMap

/**
 * A Select selects elements from a stream of tabular rows.
 * (Like in SQL Selects), transforms and filters them
 *
 * Mantik only supports a subset of SQL Selects yet, however
 * it's a design goal that improvements should be solved similar to
 * SQL if applicable.
 *
 * @param projections the columns which are returned
 * @param selection AND-concatenated filters
 */
case class Select(
    projections: List[SelectProjection],
    selection: List[Condition] = Nil
) {
  lazy val resultingType: TabularData = {
    TabularData(
      ListMap(
        projections.map { column =>
          column.columnName -> column.expression.dataType
        }: _*
      )
    )
  }
}

object Select {
  /** Build a select statement. */
  def parse(input: TabularData, statement: String): Either[String, Select] = {
    SelectBuilder.buildSelect(input, statement)
  }

  /**
   * Parse and run a select statement.
   * @throws IllegalArgumentException on invalid select statements or non tabular bundles
   * @throws FeatureNotSupported when the statement can be parsed, but not executed.
   */
  def run(input: Bundle, statement: String): Bundle = {
    val tabularData = input.model match {
      case t: TabularData => t
      case other          => throw new IllegalArgumentException("Select statements only supported for tabular data")
    }
    val select = SelectBuilder.buildSelect(tabularData, statement) match {
      case Left(error)   => throw new IllegalArgumentException(error)
      case Right(select) => select
    }
    val selectRunner = new SelectRunner(select)
    selectRunner.run(input)
  }
}

/** A Single Column in a select statement. */
case class SelectProjection(
    columnName: String,
    expression: Expression
)

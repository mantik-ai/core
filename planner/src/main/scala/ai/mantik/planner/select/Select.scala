package ai.mantik.planner.select

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.{ DataType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ AlgorithmDefinition, Mantikfile }
import ai.mantik.planner.select.builder.SelectBuilder
import ai.mantik.planner.select.run.SelectRunner
import ai.mantik.planner.select.run.Compiler

import scala.collection.immutable.ListMap

/**
 * A Select selects elements from a stream of tabular rows.
 * (Like in SQL Selects), transforms and filters them
 *
 * Mantik only supports a subset of SQL Selects yet, however
 * it's a design goal that improvements should be solved similar to
 * SQL if applicable.
 *
 * @param projections the columns which are returned, if None all are returned.
 * @param selection AND-concatenated filters
 */
case class Select(
    inputType: TabularData,
    projections: Option[List[SelectProjection]] = None,
    selection: List[Condition] = Nil
) {

  def resultingType: TabularData = {
    projections match {
      case None => inputType
      case Some(projections) =>
        TabularData(
          ListMap(
            projections.map { column =>
              column.columnName -> column.expression.dataType
            }: _*
          )
        )
    }
  }

  /** Convert the selection back to an equivalent statement. */
  def toSelectStatement: String = {
    SqlSelectFormatter.formatSql(this)
  }

  /**
   * Compile select statement to a select mantikfile.
   * @return either an error or a mantikfile which can execute the selection.
   */
  def compileToSelectMantikfile(): Either[String, Mantikfile[AlgorithmDefinition]] = {
    val selectProgram = Compiler.compile(this)
    selectProgram.map { program =>

      val functionType = FunctionType(
        inputType,
        resultingType
      )

      SelectMantikfileBuilder(program, functionType).toMantikfile
    }
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

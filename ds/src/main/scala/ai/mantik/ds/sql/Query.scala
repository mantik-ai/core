package ai.mantik.ds.sql

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.TabularData
import ai.mantik.ds.element.{ Bundle, TabularBundle }
import ai.mantik.ds.sql.builder.{ QueryBuilder, SelectBuilder }
import ai.mantik.ds.sql.run.{ Compiler, TableGeneratorProgramRunner }

import scala.collection.immutable.ListMap
import cats.implicits._

/** Base trait for a Query. */
sealed trait Query {
  def resultingType: TabularData

  def toStatement: String = SqlFormatter.formatSql(this)

  /** Execute this query on a number of bundles */
  def run(inputs: TabularBundle*): Either[String, TabularBundle] = {
    Compiler.compile(this).flatMap { tabularGenerator =>
      val runner = new TableGeneratorProgramRunner(tabularGenerator)
      try {
        Right(runner.run(inputs.toVector, resultingType))
      } catch {
        case i: IllegalArgumentException => Left(i.getMessage)
      }
    }
  }

  /** Figure out input port assignment. */
  private[mantik] def figureOutInputPorts: Either[String, Vector[TabularData]] = {
    def sub(query: Query): Map[Int, TabularData] = {
      query match {
        case u: Union          => sub(u.left) ++ sub(u.right)
        case s: Select         => sub(s.input)
        case i: AnonymousInput => Map(i.slot -> i.inputType)
      }
    }
    val asMap = sub(this)
    val size = asMap.size
    (0 until size).map { idx =>
      asMap.get(idx) match {
        case Some(td) => Right(td)
        case None     => Left(s"Non Continous Input index ${idx}")
      }
    }.toVector.sequence
  }
}

object Query {
  def parse(statement: String)(implicit context: SqlContext): Either[String, Query] = {
    QueryBuilder.buildQuery(statement)
  }
}

/**
 * Anonymous input (e.g. plain select on given TabularType)
 * @param inputType type of input
 * @param slot the anonymous slot (for multiple inputs)
 */
case class AnonymousInput(
    inputType: TabularData,
    slot: Int = 0
) extends Query {
  override def resultingType: TabularData = inputType
}

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
    input: Query,
    projections: Option[List[SelectProjection]] = None,
    selection: List[Condition] = Nil
) extends Query {

  def inputType: TabularData = input.resultingType

  def resultingType: TabularData = {
    projections match {
      case None => input.resultingType
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
}

object Select {
  /** Build a select statement. */
  def parse(input: TabularData, statement: String): Either[String, Select] = {
    SelectBuilder.buildSelect(input, statement)
  }

  /** Build a select statement with given context. */
  def parse(statement: String)(implicit sqlContext: SqlContext): Either[String, Select] = {
    SelectBuilder.buildSelect(statement)
  }

  /**
   * Parse and run a select statement.
   * @throws IllegalArgumentException on invalid select statements or non tabular bundles
   * @throws FeatureNotSupported when the statement can be parsed, but not executed.
   */
  def run(input: TabularBundle, statement: String): Bundle = {
    val tabularData = input.model
    val select = SelectBuilder.buildSelect(tabularData, statement) match {
      case Left(error)   => throw new IllegalArgumentException(error)
      case Right(select) => select
    }
    select.run(input) match {
      case Left(error) => throw new FeatureNotSupported("Could not execute query")
      case Right(ok)   => ok
    }
  }
}

/** A Single Column in a select statement. */
case class SelectProjection(
    columnName: String,
    expression: Expression
)

/** An UNION. */
case class Union(
    left: Query,
    right: Query,
    all: Boolean
) extends Query {

  override def resultingType: TabularData = left.resultingType

  /** Gives a flat representation of this union. */
  def flat: Vector[Query] = {
    left match {
      case leftUnion: Union if leftUnion.all == all =>
        leftUnion.flat :+ right
      case other =>
        Vector(left, right)
    }
  }
}
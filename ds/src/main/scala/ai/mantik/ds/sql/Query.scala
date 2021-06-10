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
package ai.mantik.ds.sql

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.{DataType, TabularData}
import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.ds.sql.builder.{JoinBuilder, QueryBuilder, SelectBuilder}
import ai.mantik.ds.sql.run.{Compiler, SingleTableGeneratorProgramRunner}
import cats.implicits._
import org.slf4j.LoggerFactory

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

/** Base trait for a Query. */
sealed trait Query {
  def resultingQueryType: QueryTabularType

  /** Resulting tabular type, shadowing if necessary. */
  lazy val resultingTabularType: TabularData = resultingQueryType.forceToTabularType

  def toStatement: String = SqlFormatter.formatSql(this)

  /** Execute this query on a number of bundles */
  def run(inputs: TabularBundle*): Either[String, TabularBundle] = {
    for {
      tabularGenerator <- Compiler.compile(this)
      result <-
        try {
          val runner = new SingleTableGeneratorProgramRunner(tabularGenerator)
          Right(runner.run(inputs.toVector))
        } catch {
          case NonFatal(e) =>
            Query.logger.warn(s"Could not execute query", e)
            Left(s"Query Execution failed ${e}")
        }
    } yield result
  }

  /** Figure out input port assignment. */
  private[mantik] def figureOutInputPorts: Either[String, Vector[TabularData]] = {
    def sub(query: Query): Map[Int, TabularData] = {
      query match {
        case u: Union          => sub(u.left) ++ sub(u.right)
        case s: Select         => sub(s.input)
        case j: Join           => sub(j.left) ++ sub(j.right)
        case i: AnonymousInput => Map(i.slot -> i.inputType)
        case a: Alias          => sub(a.query)
      }
    }

    val asMap = sub(this)
    val size = asMap.size
    (0 until size)
      .map { idx =>
        asMap.get(idx) match {
          case Some(td) => Right(td)
          case None     => Left(s"Non Continous Input index ${idx}")
        }
      }
      .toVector
      .sequence
  }
}

object Query {
  private val logger = LoggerFactory.getLogger(getClass)
  def parse(statement: String)(implicit context: SqlContext): Either[String, Query] = {
    QueryBuilder.buildQuery(statement)
  }

  /** Run a statement on tabular arguments which can be referenced by &#36;0, &#36;1, ... */
  def run(statement: String, arguments: TabularBundle*): Either[String, TabularBundle] = {
    implicit val context = SqlContext(arguments.map(_.model).toVector)
    for {
      parsed <- QueryBuilder.buildQuery(statement)
      result <- parsed.run(arguments: _*)
    } yield result
  }
}

/**
  * Anonymous input (e.g. plain select on given TabularType)
  *
  * @param inputType type of input
  * @param slot      the anonymous slot (for multiple inputs)
  */
case class AnonymousInput(
    inputType: TabularData,
    slot: Int = 0
) extends Query {
  override def resultingQueryType: QueryTabularType = QueryTabularType.fromTabularData(inputType)
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
  * @param selection   AND-concatenated filters
  */
case class Select(
    input: Query,
    projections: Option[Vector[SelectProjection]] = None,
    selection: Vector[Condition] = Vector.empty
) extends Query {

  def inputType: QueryTabularType = input.resultingQueryType

  def inputTabularType: TabularData = inputType.forceToTabularType

  def resultingQueryType: QueryTabularType = {
    projections match {
      case None => input.resultingQueryType
      case Some(projections) =>
        QueryTabularType(
          projections.map { column =>
            QueryColumn(column.columnName, column.expression.dataType)
          }
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
    */
  @throws[IllegalArgumentException]("on invalid select statements or non tabular bundles")
  @throws[FeatureNotSupported]("when the statement can be parsed, but not executed.")
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

  override def resultingQueryType: QueryTabularType = left.resultingQueryType

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

/** Different Jon Types. */
sealed abstract class JoinType(val sqlName: String)

object JoinType {

  case object Inner extends JoinType("INNER")

  case object Left extends JoinType("LEFT")

  case object Right extends JoinType("RIGHT")

  case object Outer extends JoinType("FULL OUTER")

  val All: Seq[JoinType] = Seq(Inner, Left, Right, Outer)
}

/**
  * A Join Query
  *
  * @param left                      left query
  * @param right                     right query
  * @param joinType                  kind of join
  * @param condition                 condition Join Condition
  */
case class Join(
    left: Query,
    right: Query,
    joinType: JoinType,
    condition: JoinCondition
) extends Query {

  /** The tabular data used within join conditions. */
  lazy val innerType: QueryTabularType = JoinBuilder.innerTabularData(left, right, joinType)

  override lazy val resultingQueryType: QueryTabularType = {
    val idDrops = condition match {
      case JoinCondition.Using(columns) => columns.map(_.dropId)
      case _                            => Vector.empty
    }
    innerType.dropByIds(idDrops: _*)
  }
}

object Join {

  /** Build an anonymous join using given columns from two anonymous data sources. */
  def anonymousFromUsing(
      left: TabularData,
      right: TabularData,
      using: Seq[String],
      joinType: JoinType
  ): Either[String, Join] = {
    JoinBuilder.buildAnonymousUsing(left, right, using, joinType)
  }
}

/** Join Conditions. */
sealed trait JoinCondition

object JoinCondition {

  case class On(
      expression: Condition
  ) extends JoinCondition

  case class Using(
      columns: Vector[UsingColumn]
  ) extends JoinCondition

  /**
    * Defines a column used in Using-Comparison.
    * @param name name of the column
    * @param caseSensitive if true the column name is case sensitive
    * @param leftId resolved id on the left side
    * @param rightId resolved id on the right side
    * @param dropId which id of the inner model is dropped in the result
    * @param dataType comparison datatype
    */
  case class UsingColumn(
      name: String,
      caseSensitive: Boolean = false,
      leftId: Int,
      rightId: Int,
      dropId: Int,
      dataType: DataType
  )

  case object Cross extends JoinCondition
}

/** A Name alias. */
case class Alias(
    query: Query,
    name: String
) extends Query {
  override lazy val resultingQueryType: QueryTabularType = query.resultingQueryType.withAlias(name)
}

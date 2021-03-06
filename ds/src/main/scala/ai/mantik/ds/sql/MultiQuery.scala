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

import ai.mantik.ds.TabularData
import ai.mantik.ds.element.TabularBundle
import ai.mantik.ds.sql.builder.{MultiQueryBuilder, QueryBuilder}
import ai.mantik.ds.sql.run.{
  Compiler,
  MultiTableGeneratorProgram,
  MultiTableGeneratorProgramRunner,
  SingleTableGeneratorProgram,
  SingleTableGeneratorProgramRunner
}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

/** Combines multiples queries (returning multiple tabular data streams) */
sealed trait MultiQuery {

  /** Resulting query types. */
  def resultingQueryType: Vector[QueryTabularType]

  /** Execute a MultiQuery on tabular bundles. */
  def run(inputs: TabularBundle*): Either[String, Vector[TabularBundle]] = {
    for {
      tabularGenerator <- Compiler.compile(this)
      result <-
        try {
          tabularGenerator match {
            case single: SingleTableGeneratorProgram =>
              val runner = new SingleTableGeneratorProgramRunner(single)
              val result = runner.run(inputs.toVector)
              Right(Vector(result))
            case multi: MultiTableGeneratorProgram =>
              val runner = new MultiTableGeneratorProgramRunner(multi)
              val result = runner.run(inputs.toVector)
              Right(result)
          }
        } catch {
          case NonFatal(e) =>
            MultiQuery.logger.warn(s"Could not execute query", e)
            Left(s"Query Execution failed ${e}")
        }
    } yield result
  }

  /** Figure out input port assignment. */
  private[mantik] def figureOutInputPorts: Either[String, Vector[TabularData]]

  /** Converts the query back to a statement. */
  def toStatement: String = SqlFormatter.formatSql(this)
}

object MultiQuery {
  private val logger = LoggerFactory.getLogger(getClass)

  def parse(statement: String)(implicit context: SqlContext): Either[String, MultiQuery] = {
    MultiQueryBuilder.buildQuery(statement)
  }
}

/** A Single [[Query]] as [[MultiQuery]]. */
case class SingleQuery(query: Query) extends MultiQuery {
  override def resultingQueryType: Vector[QueryTabularType] = Vector(query.resultingQueryType)

  override private[mantik] def figureOutInputPorts: Either[String, Vector[TabularData]] = query.figureOutInputPorts
}

/**
  * A Split operation.
  * @param query input data
  * @param shuffleSeed if given, shuffle data with the given seed
  * @param fractions the splitting fractions [0.0 .. 1.0], remaining data will form the last table.
  *                  resulting data will consist of N + 1 tables.
  */
case class Split(
    query: Query,
    fractions: Vector[Double],
    shuffleSeed: Option[Long] = None
) extends MultiQuery {

  def resultCount: Int = fractions.size + 1

  override def resultingQueryType: Vector[QueryTabularType] = {
    val rt = query.resultingQueryType
    Vector.fill(resultCount)(rt)
  }

  override private[mantik] def figureOutInputPorts: Either[String, Vector[TabularData]] = query.figureOutInputPorts
}

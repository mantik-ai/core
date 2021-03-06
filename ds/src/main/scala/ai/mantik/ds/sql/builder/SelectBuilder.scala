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
package ai.mantik.ds.sql.builder

import ai.mantik.ds.{TabularData, sql}
import ai.mantik.ds.sql.{AnonymousInput, Condition, Query, QueryTabularType, Select, SelectProjection, SqlContext}
import ai.mantik.ds.sql.parser.{AST, QueryParser, SelectParser}
import cats.implicits._

import scala.annotation.tailrec

/** Build Select's from AST Nodes. */
private[sql] object SelectBuilder {

  /**
    * Builds a select statement for a given input data on slot 0
    * Returns either an error or a select statement
    */
  def buildSelect(input: TabularData, statement: String): Either[String, Select] = {
    implicit val context = SqlContext(
      anonymous = Vector(input)
    )
    buildSelect(statement)
  }

  def buildSelect(statement: String)(implicit context: SqlContext): Either[String, Select] = {
    for {
      node <- QueryParser.parseSelectToNode(statement)
      build <- buildSelectFromParsed(node)
    } yield build
  }

  def buildSelectFromParsed(statement: AST.SelectNode)(implicit context: SqlContext): Either[String, Select] = {
    val from = statement.from.getOrElse(
      AST.AnonymousReference(0)
    )
    for {
      input <- QueryBuilder.buildQueryFromParsed(from)
      inputType = input.resultingQueryType
      projections <- buildProjections(inputType, statement)
      selectors <- buildSelectors(inputType, statement)
    } yield sql.Select(input, projections, selectors)
  }

  private def buildProjections(
      input: QueryTabularType,
      statement: AST.SelectNode
  ): Either[String, Option[Vector[SelectProjection]]] = {
    if (statement.isAll) {
      Right(
        None
      )
    } else {
      statement.selectColumns.zipWithIndex
        .map { case (selectColumnNode, idx) =>
          buildProjection(input, selectColumnNode, idx)
        }
        .sequence
        .map(Some(_))
    }
  }

  private def buildSelectors(input: QueryTabularType, statement: AST.SelectNode): Either[String, Vector[Condition]] = {
    statement.where match {
      case None => Right(Vector.empty)
      case Some(expression) =>
        SelectorBuilder.convertSelector(input, expression)
    }
  }

  private def buildProjection(
      input: QueryTabularType,
      node: AST.SelectColumnNode,
      idx: Int
  ): Either[String, SelectProjection] = {
    val name = guessName(node, idx)
    for {
      expression <- ExpressionBuilder.convertExpression(input, node.expression)
    } yield SelectProjection(name, expression)
  }

  private def guessName(node: AST.SelectColumnNode, idx: Int): String = {
    node.as match {
      case Some(identifier) => identifier.name
      case None =>
        guessName(node.expression).getOrElse(
          // choose artificial name
          "$" + (idx + 1).toString // Starting with 1 looks better
        )
    }
  }

  @tailrec
  private def guessName(node: AST.ExpressionNode): Option[String] = {
    node match {
      case id: AST.IdentifierNode   => Some(id.name)
      case id: AST.StructAccessNode => Some(id.name)
      case cast: AST.CastNode =>
        guessName(cast.expression)
      case _ => None
    }
  }

}

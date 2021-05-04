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

import ai.mantik.ds.sql.{Alias, AnonymousInput, Query, SqlContext, Union}
import ai.mantik.ds.sql.parser.{AST, QueryParser}

/** Builds queries from AST Nodes */
private[sql] object QueryBuilder {

  /** Build a query from a statement string. */
  def buildQuery(statement: String)(implicit context: SqlContext): Either[String, Query] = {
    for {
      queryNode <- QueryParser.parseQueryNode(statement)
      query <- buildQueryFromParsed(queryNode)
    } yield query
  }

  /** Build a Query from a parsed entry. */
  def buildQueryFromParsed(query: AST.QueryNode)(implicit context: SqlContext): Either[String, Query] = {
    query match {
      case a: AST.AnonymousReference =>
        buildAnonymousInput(a)
      case s: AST.SelectNode =>
        SelectBuilder.buildSelectFromParsed(s)
      case u: AST.UnionNode =>
        buildUnionFromParsed(u)
      case j: AST.JoinNode =>
        JoinBuilder.buildJoinFromParsed(j)
      case a: AST.AliasNode =>
        buildQueryFromParsed(a.query).map { inner =>
          Alias(inner, a.name)
        }
    }
  }

  def buildAnonymousInput(a: AST.AnonymousReference)(implicit context: SqlContext): Either[String, AnonymousInput] = {
    if (context.anonymous.isDefinedAt(a.id)) {
      val dataType = context.anonymous(a.id)
      Right(AnonymousInput(dataType, a.id))
    } else {
      Left(s"Could not resolve anonymous input ${a}")
    }
  }

  def buildUnionFromParsed(union: AST.UnionNode)(implicit context: SqlContext): Either[String, Union] = {
    for {
      leftQuery <- buildQueryFromParsed(union.left)
      rightQuery <- buildQueryFromParsed(union.right)
      _ <- validateSameType(leftQuery, rightQuery)
    } yield Union(leftQuery, rightQuery, union.all)
  }

  private def validateSameType(left: Query, right: Query): Either[String, Unit] = {
    if (left.resultingQueryType == right.resultingQueryType) {
      Right(())
    } else {
      Left(s"Type mismatch, left: ${left.resultingQueryType}, right: ${right.resultingQueryType}")
    }
  }
}

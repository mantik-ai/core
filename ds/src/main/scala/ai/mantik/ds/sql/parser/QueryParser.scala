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
package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.{MultiQueryNode, QueryNode, SelectNode}
import org.parboiled2.{ParseError, Parser, ParserInput, Rule1}
import Parser.DeliveryScheme.Either

import scala.collection.immutable

object QueryParser {

  /** Parse a SELECT statement into a SelectNode */
  def parseSelectToNode(select: String): Either[String, SelectNode] = {
    parseQueryNode(select).right.flatMap {
      case s: SelectNode => Right(s)
      case other         => Left(s"Expected SELECT, got ${other.getClass.getSimpleName}")
    }
  }

  /** Parse a Query */
  def parseQueryNode(statement: String): Either[String, QueryNode] = {
    val parser = new QueryParser(statement)
    val result = parser.FullQuery.run()
    decodeResult(parser, result)
  }

  /** Parse a Multi Query */
  def parseMultiQuery(statement: String): Either[String, MultiQueryNode] = {
    val parser = new QueryParser(statement)
    val result = parser.MultiQueryEOI.run()
    decodeResult(parser, result)
  }

  private def decodeResult[T](p: Parser, result: Either[ParseError, T]): Either[String, T] = {
    result.left.map { err =>
      p.formatError(err)
    }
  }
}

private[parser] class QueryParser(val input: ParserInput)
    extends Parser
    with SelectParser
    with UnionParser
    with JoinParser
    with AliasParser
    with MultiQueryParser
    with AnonymousOnlyInnerQueryParser {

  def FullQuery: Rule1[AST.QueryNode] = rule {
    Query ~ optional(ParseAsAlias) ~ EOI ~> { withOptionalAlias(_, _) }
  }

  def Query: Rule1[AST.QueryNode] = rule {
    Union | Join | AnonymousFrom | Select
  }

  override def SelectLikeInnerQuery: Rule1[QueryNode] = rule {
    Join | AnonymousFromMaybeAliases | BracketInnerQuery
  }

  override def UnionLikeInnerQuery: Rule1[QueryNode] = rule {
    AnonymousFromMaybeAliases | Select | BracketInnerQuery
  }

  override def JoinLikeInnerQuery: Rule1[QueryNode] = rule {
    AnonymousFromMaybeAliases | BracketInnerQuery
  }

  private def AnonymousFromMaybeAliases: Rule1[QueryNode] = rule {
    AnonymousFrom ~ optional(ParseAsAlias) ~> { withOptionalAlias(_, _) }
  }

  def BracketInnerQuery: Rule1[QueryNode] = rule {
    symbolw('(') ~ Query ~ symbolw(')')
  }
}

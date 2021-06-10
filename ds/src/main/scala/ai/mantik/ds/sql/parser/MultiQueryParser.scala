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

import ai.mantik.ds.sql.parser.AST.QueryNode
import org.parboiled2.{Parser, Rule1}

import scala.collection.immutable

/**
  * Parses Multi queries
  * (Special queries which can return multiple tables)
  */
private[parser] trait MultiQueryParser {
  self: Parser with ConstantParser with ExpressionParser =>

  def BracketInnerQuery: Rule1[QueryNode]

  def Query: Rule1[AST.QueryNode]

  def MultiQueryEOI: Rule1[AST.MultiQueryNode] = rule {
    MultiQuery ~ EOI
  }

  def MultiQuery: Rule1[AST.MultiQueryNode] = rule {
    Split | SingleQuery
  }

  def SingleQuery: Rule1[AST.SingleQuery] = rule {
    Query ~> { query =>
      AST.SingleQuery(query)
    }
  }

  def Split: Rule1[AST.Split] = rule {
    keyword("split") ~
      BracketInnerQuery ~
      keyword("at") ~
      oneOrMore(NumberExpression).separatedBy(symbolw(',')) ~
      optional(
        keyword("with") ~ keyword("shuffle") ~ NumberExpression
      ) ~> { (innerQuery: AST.QueryNode, splits: immutable.Seq[AST.NumberNode], shuffle: Option[AST.NumberNode]) =>
        AST.Split(
          innerQuery,
          splits.toVector,
          shuffle
        )
      }
  }
}

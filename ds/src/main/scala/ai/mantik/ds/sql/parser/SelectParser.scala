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

import ai.mantik.ds.sql.parser.AST.{AnonymousReference, ExpressionNode, QueryNode, SelectColumnNode, SelectNode}
import org.parboiled2.{ParseError, Parser, ParserInput, Rule1}

import scala.collection.immutable

private[parser] trait SelectParser extends ExpressionParser with InnerQueryParser {
  self: Parser =>
  def Select: Rule1[SelectNode] = rule {
    (keyword("select") ~ SelectColumns ~
      optional(keyword("from") ~ SelectLikeInnerQuery) ~
      optional(keyword("where") ~ Expression)) ~> { (columns, from, where) =>
      SelectNode(columns, where, from)
    }
  }

  def SelectColumns: Rule1[Vector[SelectColumnNode]] = rule {
    SelectAllColumns | SelectSomeColumns
  }

  def SelectAllColumns: Rule1[Vector[SelectColumnNode]] = rule {
    symbolw('*') ~ push(Vector.empty[SelectColumnNode])
  }

  def SelectSomeColumns: Rule1[Vector[SelectColumnNode]] = rule {
    oneOrMore(SelectColumn).separatedBy(symbolw(',')) ~> { elements: immutable.Seq[SelectColumnNode] =>
      elements.toVector
    }
  }

  def SelectColumn: Rule1[SelectColumnNode] = rule {
    (Expression ~ optional(keyword("as") ~ Identifier)) ~> { (expression, asValue) =>
      AST.SelectColumnNode(expression, asValue)
    }
  }
}

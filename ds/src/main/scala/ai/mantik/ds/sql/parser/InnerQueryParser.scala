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

import ai.mantik.ds.sql.parser.AST.AnonymousReference
import org.parboiled2.{Parser, Rule1}

/** Defines an inner query parser, implemented by [[QueryParser]] */
trait InnerQueryParser {
  self: Parser =>

  /** Parse an inner query as expected in InnerQuery union InnerQuery */
  def UnionLikeInnerQuery: Rule1[AST.QueryNode]

  /** Parse an inner query as expected in SELECT [..] FROM InnerQuery */
  def SelectLikeInnerQuery: Rule1[AST.QueryNode]

  /** Parse an inner query as expeced in JOINs */
  def JoinLikeInnerQuery: Rule1[AST.QueryNode]
}

/** Implements InnerQueryParser with only support for anonymous queries */
trait AnonymousOnlyInnerQueryParser extends InnerQueryParser with ConstantParser {
  self: Parser =>

  override def UnionLikeInnerQuery: Rule1[AST.QueryNode] = AnonymousFrom

  override def SelectLikeInnerQuery: Rule1[AST.QueryNode] = AnonymousFrom

  override def JoinLikeInnerQuery: Rule1[AST.QueryNode] = AnonymousFrom

  def AnonymousFrom: Rule1[AnonymousReference] = rule {
    "$" ~ capture(Digits) ~ Whitespace ~> { s: String =>
      AnonymousReference(s.toInt)
    }
  }
}

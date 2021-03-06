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

import ai.mantik.ds.Errors.TypeNotFoundException
import ai.mantik.ds.{ArrayT, FundamentalType, ImageChannel}
import org.parboiled2._

/** Parser for Expressions. */
private[parser] trait ExpressionParser extends ConstantParser with BinaryOperationParser {
  this: Parser =>

  import AST._

  /** Parses an expression and consumes all input. */
  def ExpressionEOI: Rule1[ExpressionNode] = rule {
    Expression ~ EOI
  }

  def BaseExpression: Rule1[ExpressionNode] = rule {
    BracketExpression | Cast | Constant | UnaryExpression | Identifier
  }

  private def BracketExpression: Rule1[ExpressionNode] = rule {
    symbolw('(') ~ Expression ~ symbolw(')') ~ optional(StructAccess) ~> {
      (expression: ExpressionNode, access: Option[String]) =>
        access match {
          case Some(access) => AST.StructAccessNode(expression, access)
          case None         => expression
        }
    }
  }

  private def StructAccess: Rule1[String] = rule {
    "." ~ capture(oneOrMore(IdentifierChar))
  }

  def Identifier: Rule1[IdentifierNode] = rule {
    EscapedIdentifier | UnescapedIdentifier
  }

  def UnaryExpression: Rule1[UnaryOperationNode] = rule {
    capture(oneOrMore(IdentifierChar)) ~ Whitespace ~ symbolw('(') ~ Expression ~ symbolw(')') ~> {
      (name: String, exp: ExpressionNode) =>
        UnaryOperationNode(name.trim.toLowerCase, exp)
    }
  }

  def UnescapedIdentifier: Rule1[IdentifierNode] = rule {
    capture(oneOrMore(IdentifierChar)) ~> { s: String =>
      IdentifierNode(s)
    } ~ Whitespace
  }

  def EscapedIdentifier: Rule1[IdentifierNode] = rule {
    "\"" ~ capture(zeroOrMore(QuotedIdentifierChar)) ~> (s => IdentifierNode(s, ignoreCase = false)) ~ "\"" ~ Whitespace
  }

  def QuotedIdentifierChar: Rule0 = rule {
    noneOf("\"")
  }

  def IdentifierChar: Rule0 = rule {
    noneOf("\", ()<>=!+-[]")
  }

  def Cast: Rule1[CastNode] = rule {
    keyword("cast") ~ symbolw('(') ~ Expression ~ keyword("as") ~ Whitespace ~ Type ~ symbolw(')') ~> CastNode
  }

  def Type: Rule1[TypeNode] = rule {
    (TensorType | ImageType | FundamentalTypeN) ~ Whitespace ~ ArrayNesting ~ Whitespace ~ optionalKeyword(
      "nullable"
    ) ~> { (dt: TypeNode, arrayNesting: Int, nullable: Boolean) =>
      val arrayIfNecessary = (0 until arrayNesting).foldLeft(dt) { (c, _) => ArrayTypeNode(c) }
      if (nullable) {
        NullableTypeNode(arrayIfNecessary)
      } else {
        arrayIfNecessary
      }
    }
  }

  def ArrayNesting: Rule1[Int] = rule {
    zeroOrMore("[]" ~ push(1)) ~> { elements: scala.collection.immutable.Seq[Int] =>
      elements.sum
    }
  }

  def TensorType: Rule1[TypeNode] = rule {
    keyword("tensor") ~ optional(keyword("of") ~ FundamentalTypeN) ~> { underlying: Option[FundamentalTypeNode] =>
      TensorTypeNode(underlying.map(_.ft))
    }
  }

  def ImageType: Rule1[TypeNode] = rule {
    keyword("image") ~ optional(keyword("of") ~ FundamentalTypeN) ~ optional(
      Whitespace ~ keyword("in") ~ ImageChannelN
    ) ~> { (underlying: Option[FundamentalTypeNode], channel: Option[ImageChannel]) =>
      ImageTypeNode(underlying.map(_.ft), channel)
    }
  }

  def FundamentalTypeN: Rule1[FundamentalTypeNode] = rule {
    capture(oneOrMore(IdentifierChar)) ~> { s: String =>
      test(isValidFundamentalType(s)) ~ push(
        FundamentalTypeNode(FundamentalType.fromName(s).get)
      )
    }
  }

  def ImageChannelN: Rule1[ImageChannel] = rule {
    capture(oneOrMore(IdentifierChar)) ~> { s: String =>
      test(isValidChannel(s)) ~ push(
        ImageChannel.fromName(s).get
      )
    }
  }

  private def isValidFundamentalType(s: String): Boolean = {
    FundamentalType.fromName(s).isDefined
  }

  private def isValidChannel(s: String): Boolean = {
    ImageChannel.fromName(s).isDefined
  }

  /** Consumes a symbol plus whitespace. */
  def symbolw(char: Char): Rule0 = rule {
    char ~ Whitespace
  }
}

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

import ai.mantik.ds.sql.parser.AST._
import org.parboiled2._

trait ConstantParser {
  self: Parser =>

  def Constant: Rule1[ConstantExpressionNode] = rule {
    StringExpression | NumberExpression | BoolExpression | Null | VoidExpression
  }

  def NumberExpression: Rule1[NumberNode] = rule {
    NumericExpressionUnwrapped ~> { x: String =>
      NumberNode(BigDecimal(x))
    }
  }

  private def NumericExpressionUnwrapped: Rule1[String] = rule {
    capture(Integer ~ optional(Frac ~ optional(Exp))) ~ Whitespace
  }

  private def Integer = rule {
    optional(anyOf("+-")) ~ (("1" - "9") ~ Digits | Digit)
  }

  private def Frac = rule {
    "." ~ Digits
  }

  private def Exp = rule {
    ignoreCase("e") ~ optional(anyOf("+-")) ~ Digits
  }

  def Digits: Rule0 = rule {
    oneOrMore(Digit)
  }

  def Digit: Rule0 = rule {
    "0" - "9"
  }

  def StringExpression: Rule1[StringNode] = rule {
    StringUnwrapped ~> (buildStringNodeFromStringWithQuotes(_))
  }

  private def StringUnwrapped: Rule1[String] = rule {
    "'" ~ capture(zeroOrMore(Character)) ~ "'" ~ Whitespace
  }

  private def buildStringNodeFromStringWithQuotes(s: String): StringNode = {
    StringNode(s.replace("''", "'"))
  }

  def Character: Rule0 = rule {
    EscapedChar | NormalChar
  }

  def EscapedChar: Rule0 = rule {
    str("''")
  }

  def NormalChar: Rule0 = rule {
    !anyOf("'") ~ ANY
  }

  def BoolExpression: Rule1[BoolNode] = rule { True | False }

  def True: Rule1[BoolNode] = rule { keyword("true") ~ push(BoolNode(true)) }

  def False: Rule1[BoolNode] = rule { keyword("false") ~ push(BoolNode(false)) }

  def Null: Rule1[NullNode.type] = rule { keyword("null") ~ push(NullNode) }

  def VoidExpression: Rule1[VoidNode.type] = rule { keyword("void") ~ push(VoidNode) }

  def Whitespace: Rule0 = rule { zeroOrMore(anyOf(" \n\r\t\f")) }

  /** Consumes a keyword (ignoring case) plus whitespace. */
  def keyword(string: String): Rule0 = rule {
    ignoreCase(string) ~ Whitespace
  }

  /** Checks for an optional keyword, returns true if it's present. */
  def optionalKeyword(string: String): Rule1[Boolean] = rule {
    optional(keyword(string) ~ push(true)) ~> { s: Option[Boolean] =>
      s.getOrElse(false)
    }
  }
}

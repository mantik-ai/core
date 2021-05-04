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

import ai.mantik.ds.sql.parser.AST.{BinaryOperationNode, IdentifierNode}
import org.parboiled2._

class BinaryOperationParserSpec extends ParserTestBase {

  class ParserImpl(val input: ParserInput) extends Parser with ExpressionParser

  override protected def makeParser(s: String) = new ParserImpl(s)

  "precende" should "be ok" in {
    testEquality(
      _.Expression,
      "A + B * C",
      BinaryOperationNode("+", IdentifierNode("A"), BinaryOperationNode("*", IdentifierNode("B"), IdentifierNode("C")))
    )
    testEquality(
      _.Prio4BinaryOperation,
      "A * B + C",
      BinaryOperationNode(
        "+",
        BinaryOperationNode("*", IdentifierNode("A"), IdentifierNode("B")),
        IdentifierNode("C")
      )
    )
    testEquality(
      _.Expression,
      "A * B + C",
      BinaryOperationNode(
        "+",
        BinaryOperationNode("*", IdentifierNode("A"), IdentifierNode("B")),
        IdentifierNode("C")
      )
    )
    testEquality(
      _.Expression,
      "A * B and C + D",
      BinaryOperationNode(
        "and",
        BinaryOperationNode("*", IdentifierNode("A"), IdentifierNode("B")),
        BinaryOperationNode("+", IdentifierNode("C"), IdentifierNode("D"))
      )
    )
  }

  "left-associativity" should "work in a simple case" in {
    testEquality(
      _.ExpressionEOI,
      "A / B / C",
      BinaryOperationNode(
        "/",
        BinaryOperationNode(
          "/",
          IdentifierNode("A"),
          IdentifierNode("B")
        ),
        IdentifierNode("C")
      )
    )
  }

  "multiple arguments" should "work and be left-assiocative" in {
    for (sign <- Seq("+", "-", "*", "/", "and", "or")) {
      testEquality(
        _.ExpressionEOI,
        s"A $sign B $sign C",
        BinaryOperationNode(
          sign,
          BinaryOperationNode(sign, IdentifierNode("A"), IdentifierNode("B")),
          IdentifierNode("C")
        )
      )
    }
  }
}

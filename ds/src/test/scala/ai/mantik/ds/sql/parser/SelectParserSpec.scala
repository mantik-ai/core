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
import org.parboiled2.{Parser, ParserInput}

class SelectParserSpec extends ParserTestBase {

  class FullParser(val input: ParserInput) extends Parser with SelectParser with AnonymousOnlyInnerQueryParser

  override type ParserImpl = FullParser
  override protected def makeParser(s: String) = new FullParser(s)

  def parseSelectTest(s: String, expected: SelectNode): Unit = {
    it should s"parse ${s}" in {
      testEquality(_.Select, s, expected)
    }
  }

  parseSelectTest("select 1234", SelectNode(Vector(SelectColumnNode(NumberNode(1234)))))

  parseSelectTest(
    "select 1234, false",
    SelectNode(Vector(SelectColumnNode(NumberNode(1234)), SelectColumnNode(BoolNode(false))))
  )

  parseSelectTest(
    "select foo,\"bar\"",
    SelectNode(
      Vector(
        SelectColumnNode(IdentifierNode("foo")),
        SelectColumnNode(IdentifierNode("bar", ignoreCase = false))
      )
    )
  )

  parseSelectTest("select *", SelectNode())

  parseSelectTest("select * from $1", SelectNode(from = Some(AnonymousReference(1))))

  parseSelectTest(
    "select foo as bar",
    SelectNode(
      Vector(
        SelectColumnNode(IdentifierNode("foo"), as = Some(IdentifierNode("bar")))
      )
    )
  )

  parseSelectTest(
    "select foo as bar, baz",
    SelectNode(
      Vector(
        SelectColumnNode(IdentifierNode("foo"), as = Some(IdentifierNode("bar"))),
        SelectColumnNode(IdentifierNode("baz"))
      )
    )
  )

  parseSelectTest(
    "select foo where a",
    SelectNode(
      Vector(SelectColumnNode(IdentifierNode("foo"))),
      Some(
        IdentifierNode("a")
      )
    )
  )

  parseSelectTest(
    "select foo where a = b",
    SelectNode(
      Vector(SelectColumnNode(IdentifierNode("foo"))),
      Some(
        BinaryOperationNode("=", IdentifierNode("a"), IdentifierNode("b"))
      )
    )
  )

  parseSelectTest(
    "select baz,biz + 1 from $0 where a + b = c",
    SelectNode(
      Vector(
        SelectColumnNode(IdentifierNode("baz")),
        SelectColumnNode(BinaryOperationNode("+", IdentifierNode("biz"), NumberNode(BigDecimal(1))))
      ),
      Some(
        BinaryOperationNode(
          "=",
          BinaryOperationNode("+", IdentifierNode("a"), IdentifierNode("b")),
          IdentifierNode("c")
        )
      ),
      Some(AnonymousReference(0))
    )
  )
}

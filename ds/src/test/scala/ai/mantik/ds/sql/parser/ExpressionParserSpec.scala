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
import ai.mantik.ds.{FundamentalType, ImageChannel}
import org.parboiled2.{Parser, ParserInput}

class ExpressionParserSpec extends ParserTestBase {

  class FullParser(val input: ParserInput) extends Parser with ExpressionParser

  override type ParserImpl = FullParser
  override protected def makeParser(s: String) = new FullParser(s)

  def expressionTest(s: String, expected: ExpressionNode): Unit = {
    it should s"parse ${s}" in {
      testEquality(_.ExpressionEOI, s, expected)
    }
  }

  expressionTest("true", BoolNode(true))
  expressionTest("false", BoolNode(false))
  expressionTest("'hello world'", StringNode("hello world"))
  expressionTest("''", StringNode(""))
  expressionTest("'Hello '' Foo'", StringNode("Hello ' Foo"))
  expressionTest("1", NumberNode(1))
  expressionTest("(1)", NumberNode(1))
  expressionTest("+3", NumberNode(3))
  expressionTest("3", NumberNode(3))
  expressionTest("-3", NumberNode(-3))
  expressionTest("-3.5", NumberNode(-3.5))
  expressionTest("10.5", NumberNode(10.5))
  expressionTest("1.0e3", NumberNode(1000))
  expressionTest("void", VoidNode)
  expressionTest("foo", IdentifierNode("foo", ignoreCase = true))
  expressionTest("foo.bar", IdentifierNode("foo.bar"))
  expressionTest("(foo)", IdentifierNode("foo"))
  expressionTest("(foo).bar", StructAccessNode(IdentifierNode("foo"), "bar"))
  expressionTest("\"foo\"", IdentifierNode("foo", ignoreCase = false))
  expressionTest("\"foO\"", IdentifierNode("foO", ignoreCase = false))
  expressionTest("\"void\"", IdentifierNode("void", ignoreCase = false))
  expressionTest("CAST (1as int32)", CastNode(NumberNode(1), FundamentalTypeNode(FundamentalType.Int32)))
  expressionTest("CAST (1 as int32)", CastNode(NumberNode(1), FundamentalTypeNode(FundamentalType.Int32)))
  expressionTest(
    "CAST (1 as int32[])",
    CastNode(NumberNode(1), ArrayTypeNode(FundamentalTypeNode(FundamentalType.Int32)))
  )
  expressionTest(
    "CAST (1 as int32[][])",
    CastNode(NumberNode(1), ArrayTypeNode(ArrayTypeNode(FundamentalTypeNode(FundamentalType.Int32))))
  )
  expressionTest(
    "CAST (1 as int32[][] NULLABLE)",
    CastNode(NumberNode(1), NullableTypeNode(ArrayTypeNode(ArrayTypeNode(FundamentalTypeNode(FundamentalType.Int32)))))
  )
  expressionTest("CAST (TRUE as TENSOR)", CastNode(BoolNode(true), TensorTypeNode(None)))
  expressionTest(
    "CAST (TRUE as TENSOR of int32)",
    CastNode(BoolNode(true), TensorTypeNode(Some(FundamentalType.Int32)))
  )
  expressionTest("CAST (TRUE as IMAGE)", CastNode(BoolNode(true), ImageTypeNode(None, None)))
  expressionTest(
    "CAST (TRUE as IMAGE of uint8 in red)",
    CastNode(BoolNode(true), ImageTypeNode(Some(FundamentalType.Uint8), Some(ImageChannel.Red)))
  )
  expressionTest("A = B", BinaryOperationNode("=", IdentifierNode("A"), IdentifierNode("B")))
  expressionTest("A=B", BinaryOperationNode("=", IdentifierNode("A"), IdentifierNode("B")))
  expressionTest("1<>2", BinaryOperationNode("<>", NumberNode(1), NumberNode(2)))
  expressionTest("1 or 2", BinaryOperationNode("or", NumberNode(1), NumberNode(2)))
  expressionTest("1 OR 2", BinaryOperationNode("or", NumberNode(1), NumberNode(2)))
  expressionTest("1 and 2", BinaryOperationNode("and", NumberNode(1), NumberNode(2)))
  expressionTest(
    "1 and 2 and 3",
    BinaryOperationNode("and", BinaryOperationNode("and", NumberNode(1), NumberNode(2)), NumberNode(3))
  )
  expressionTest(
    "1 and 2 or 3",
    BinaryOperationNode("or", BinaryOperationNode("and", NumberNode(1), NumberNode(2)), NumberNode(3))
  )
  expressionTest(
    "1 and (2 or 3)",
    BinaryOperationNode("and", NumberNode(1), BinaryOperationNode("or", NumberNode(2), NumberNode(3)))
  )
  expressionTest(
    "1 and not (2 or 3)",
    BinaryOperationNode(
      "and",
      NumberNode(1),
      UnaryOperationNode(
        "not",
        BinaryOperationNode("or", NumberNode(2), NumberNode(3))
      )
    )
  )
  expressionTest("a IS NULL", BinaryOperationNode("is", IdentifierNode("a"), NullNode))
  expressionTest("a IS NOT NULL", BinaryOperationNode("isnot", IdentifierNode("a"), NullNode))
  expressionTest(
    "a is null or b is not null",
    BinaryOperationNode(
      "or",
      BinaryOperationNode("is", IdentifierNode("a"), NullNode),
      BinaryOperationNode("isnot", IdentifierNode("b"), NullNode)
    )
  )
  expressionTest(
    "a is null + 5",
    BinaryOperationNode(
      "is",
      IdentifierNode("a"),
      BinaryOperationNode(
        "+",
        NullNode,
        NumberNode(5)
      )
    )
  )

  expressionTest(
    "(1 and 2) or 3",
    BinaryOperationNode(
      "or",
      BinaryOperationNode("and", NumberNode(1), NumberNode(2)),
      NumberNode(3)
    )
  )

  expressionTest(
    "a[1 + 4][3]",
    BinaryOperationNode(
      "[]",
      BinaryOperationNode(
        "[]",
        IdentifierNode("a"),
        BinaryOperationNode(
          "+",
          NumberNode(1),
          NumberNode(4)
        )
      ),
      NumberNode(3)
    )
  )

  "binaryOperations" should "work" in {
    testEquality(_.ExpressionEOI, "A = B", BinaryOperationNode("=", IdentifierNode("A"), IdentifierNode("B")))
  }
}

package ai.mantik.planner.select.parser

import ai.mantik.ds.FundamentalType
import ai.mantik.planner.select.parser.AST._
import org.parboiled2.{ Parser, ParserInput }

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
  expressionTest("\"foo\"", IdentifierNode("foo", ignoreCase = false))
  expressionTest("\"foO\"", IdentifierNode("foO", ignoreCase = false))
  expressionTest("\"void\"", IdentifierNode("void", ignoreCase = false))
  expressionTest("CAST (1as int32)", CastNode(NumberNode(1), FundamentalTypeNode(FundamentalType.Int32)))
  expressionTest("CAST (1 as int32)", CastNode(NumberNode(1), FundamentalTypeNode(FundamentalType.Int32)))
  expressionTest("CAST (TRUE as TENSOR)", CastNode(BoolNode(true), TensorTypeNode))
  expressionTest("A = B", BinaryOperationNode("=", IdentifierNode("A"), IdentifierNode("B")))
  expressionTest("A=B", BinaryOperationNode("=", IdentifierNode("A"), IdentifierNode("B")))
  expressionTest("1<>2", BinaryOperationNode("<>", NumberNode(1), NumberNode(2)))
  expressionTest("1 or 2", BinaryOperationNode("or", NumberNode(1), NumberNode(2)))
  expressionTest("1 OR 2", BinaryOperationNode("or", NumberNode(1), NumberNode(2)))
  expressionTest("1 and 2", BinaryOperationNode("and", NumberNode(1), NumberNode(2)))
  expressionTest("1 and 2 and 3", BinaryOperationNode("and", BinaryOperationNode("and", NumberNode(1), NumberNode(2)), NumberNode(3)))
  expressionTest("1 and 2 or 3", BinaryOperationNode("or", BinaryOperationNode("and", NumberNode(1), NumberNode(2)), NumberNode(3)))
  expressionTest("1 and (2 or 3)", BinaryOperationNode("and", NumberNode(1), BinaryOperationNode("or", NumberNode(2), NumberNode(3))))
  expressionTest("1 and not (2 or 3)", BinaryOperationNode("and", NumberNode(1), UnaryOperationNode(
    "not",
    BinaryOperationNode("or", NumberNode(2), NumberNode(3))
  )))

  expressionTest(
    "(1 and 2) or 3",
    BinaryOperationNode(
      "or",
      BinaryOperationNode("and", NumberNode(1), NumberNode(2)),
      NumberNode(3)
    )
  )

  "binaryOperations" should "work" in {
    testEquality(_.ExpressionEOI, "A = B", BinaryOperationNode(
      "=", IdentifierNode("A"), IdentifierNode("B")))
  }
}
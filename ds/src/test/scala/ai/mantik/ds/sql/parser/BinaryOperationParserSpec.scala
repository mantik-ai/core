package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.{ BinaryOperationNode, IdentifierNode }
import org.parboiled2._

class BinaryOperationParserSpec extends ParserTestBase {

  class ParserImpl(val input: ParserInput) extends Parser with ExpressionParser

  override protected def makeParser(s: String) = new ParserImpl(s)

  "precende" should "be ok" in {
    testEquality(_.Expression, "A + B * C",
      BinaryOperationNode("+", IdentifierNode("A"),
        BinaryOperationNode("*", IdentifierNode("B"), IdentifierNode("C"))
      )
    )
    testEquality(_.Prio4BinaryOperation, "A * B + C",
      BinaryOperationNode(
        "+",
        BinaryOperationNode("*", IdentifierNode("A"), IdentifierNode("B")),
        IdentifierNode("C")
      )
    )
    testEquality(_.Expression, "A * B + C",
      BinaryOperationNode(
        "+",
        BinaryOperationNode("*", IdentifierNode("A"), IdentifierNode("B")),
        IdentifierNode("C")
      )
    )
    testEquality(_.Expression, "A * B and C + D",
      BinaryOperationNode(
        "and",
        BinaryOperationNode("*", IdentifierNode("A"), IdentifierNode("B")),
        BinaryOperationNode("+", IdentifierNode("C"), IdentifierNode("D"))
      )
    )
  }

  "left-associativity" should "work in a simple case" in {
    testEquality(_.ExpressionEOI, "A / B / C",
      BinaryOperationNode(
        "/",
        BinaryOperationNode(
          "/", IdentifierNode("A"), IdentifierNode("B")
        ), IdentifierNode("C")
      )
    )
  }

  "multiple arguments" should "work and be left-assiocative" in {
    for (sign <- Seq("+", "-", "*", "/", "and", "or")) {
      testEquality(_.ExpressionEOI, s"A $sign B $sign C",
        BinaryOperationNode(
          sign,
          BinaryOperationNode(sign, IdentifierNode("A"), IdentifierNode("B")),
          IdentifierNode("C")
        )
      )
    }
  }
}

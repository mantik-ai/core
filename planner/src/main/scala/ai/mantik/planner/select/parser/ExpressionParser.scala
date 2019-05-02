package ai.mantik.planner.select.parser

import ai.mantik.ds.FundamentalType
import org.parboiled2._

/** Parser for Expressions. */
trait ExpressionParser extends ConstantParser with BinaryOperationParser {
  this: Parser =>

  import AST._

  /** Parses an expression and consumes all input. */
  def ExpressionEOI: Rule1[ExpressionNode] = rule {
    Expression ~ EOI
  }

  def BaseExpression: Rule1[ExpressionNode] = rule { BracketExpression | Cast | Constant | UnaryExpression | Identifier }

  private def BracketExpression: Rule1[ExpressionNode] = rule {
    symbolw('(') ~ Expression ~ symbolw(')')
  }

  def Identifier: Rule1[IdentifierNode] = rule {
    EscapedIdentifier | UnescapedIdentifier
  }

  def UnaryExpression: Rule1[UnaryOperationNode] = rule {
    capture(oneOrMore(IdentifierChar)) ~ Whitespace ~ symbolw('(') ~ Expression ~ symbolw(')') ~> { (name: String, exp: ExpressionNode) =>
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
    noneOf("\", ()<>=!+-")
  }

  def Cast: Rule1[CastNode] = rule {
    keyword("cast") ~ symbolw('(') ~ Expression ~ keyword("as") ~ Whitespace ~ Type ~ symbolw(')') ~> CastNode
  }

  def Type: Rule1[TypeNode] = rule {
    capture(oneOrMore(IdentifierChar)) ~> { s: String =>
      typeNameToType(s)
    }
  }

  def typeNameToType(name: String): TypeNode = {
    if (name.toLowerCase == "tensor") {
      TensorTypeNode
    } else {
      // error handling?!
      FundamentalTypeNode(FundamentalType.fromName(name.toLowerCase))
    }
  }

  def Tensor: Rule1[TensorTypeNode.type] = rule {
    ignoreCase("tensor") ~ push(TensorTypeNode)
  }

  /** Consumes a symbol plus whitespace. */
  def symbolw(char: Char): Rule0 = rule {
    char ~ Whitespace
  }
}

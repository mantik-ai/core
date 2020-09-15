package ai.mantik.ds.sql.parser

import ai.mantik.ds.Errors.TypeNotFoundException
import ai.mantik.ds.{ FundamentalType, ImageChannel }
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
    ((TensorType | ImageType | FundamentalTypeN) ~ Whitespace ~ optionalKeyword("nullable")) ~> { (dt: TypeNode, nullable: Boolean) =>
      if (nullable) {
        NullableTypeNode(dt)
      } else {
        dt
      }
    }
  }

  def TensorType: Rule1[TypeNode] = rule {
    keyword("tensor") ~ optional(keyword("of") ~ FundamentalTypeN) ~> { underlying: Option[FundamentalTypeNode] =>
      TensorTypeNode(underlying.map(_.ft))
    }
  }

  def ImageType: Rule1[TypeNode] = rule {
    keyword("image") ~ optional(keyword("of") ~ FundamentalTypeN) ~ optional(Whitespace ~ keyword("in") ~ ImageChannelN) ~> { (underlying: Option[FundamentalTypeNode], channel: Option[ImageChannel]) =>
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

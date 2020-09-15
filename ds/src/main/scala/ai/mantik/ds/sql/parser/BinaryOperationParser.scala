package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST._
import org.parboiled2._

/** Handles parsing of Binary Operations, including precedence. */
trait BinaryOperationParser {
  self: Parser =>

  /** Parses an Expression. */
  def Expression: Rule1[ExpressionNode] = rule { Prio1BinaryOperation }

  /** Parses a Non-Binary Operation. */
  def BaseExpression: Rule1[ExpressionNode]

  def Prio5BinaryOperation: Rule1[ExpressionNode] = rule {
    BaseExpression ~ zeroOrMore(
      Prio5Operator ~ BaseExpression ~> collectOperationTuple
    ) ~> makeBinaryOperationNode
  }

  def Prio5Operator: Rule1[String] = rule {
    capture("*" | "/") ~ Whitespace
  }

  def Prio4BinaryOperation: Rule1[ExpressionNode] = rule {
    Prio5BinaryOperation ~ zeroOrMore(
      Prio4Operator ~ Prio5BinaryOperation ~> collectOperationTuple
    ) ~> makeBinaryOperationNode
  }

  def Prio4Operator: Rule1[String] = rule {
    capture("+" | "-") ~ Whitespace
  }

  // IS, ISNOT
  def Prio3BinaryOperation: Rule1[ExpressionNode] = rule {
    Prio4BinaryOperation ~ zeroOrMore(
      Prio3Operator ~ Prio4BinaryOperation ~> collectOperationTuple
    ) ~> makeBinaryOperationNode
  }

  def Prio3Operator: Rule1[String] = rule {
    capture(
      (ignoreCase("is") ~ Whitespace ~ ignoreCase("not")) |
        (ignoreCase("is") ~ Whitespace)
    ) ~ Whitespace ~> { s: String =>
        s.trim.toLowerCase.filterNot(_.isWhitespace)
      }
  }

  def Prio2BinaryOperation: Rule1[ExpressionNode] = rule {
    Prio3BinaryOperation ~ zeroOrMore(
      Prio2Operator ~ Prio3BinaryOperation ~> collectOperationTuple
    ) ~> makeBinaryOperationNode
  }

  def Prio2Operator: Rule1[String] = rule {
    capture("=" | "<>" | "<" | ">" | "<=" | ">=") ~ Whitespace
  }

  def Prio1BinaryOperation: Rule1[ExpressionNode] = rule {
    Prio2BinaryOperation ~ zeroOrMore(
      Prio1Operator ~ Prio2BinaryOperation ~> collectOperationTuple
    ) ~> makeBinaryOperationNode
  }

  def Prio1Operator: Rule1[String] = rule {
    capture(ignoreCase("and") | ignoreCase("or")) ~ Whitespace
  }

  // Helps the type checker to collect a operation tuple, how can we avoid that?
  private val collectOperationTuple: (String, ExpressionNode) => (String, ExpressionNode) = { (a, b) => (a, b) }

  val makeBinaryOperationNode: (ExpressionNode, scala.collection.immutable.Seq[(String, ExpressionNode)]) => ExpressionNode = { (b, e) =>
    // Left-Associative
    e.foldLeft(b) {
      case (current, (op, next)) =>
        BinaryOperationNode(op.trim.toLowerCase, current, next)
    }
  }

  def Whitespace: Rule0
}

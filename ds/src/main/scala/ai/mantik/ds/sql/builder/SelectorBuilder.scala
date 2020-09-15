package ai.mantik.ds.sql.builder

import ai.mantik.ds.FundamentalType.BoolType
import ai.mantik.ds.{ DataType, FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.sql.Condition.WrappedExpression
import ai.mantik.ds.sql.{ Condition, ConstantExpression, Expression }
import ai.mantik.ds.sql.parser.AST
import cats.implicits._

/** Converts Expressions into Selectors. */
private[builder] object SelectorBuilder {

  /** Convert a expression into a AND-Combined Condition list. */
  def convertSelector(input: TabularData, node: AST.ExpressionNode): Either[String, List[Condition]] = {
    node match {
      case AST.BinaryOperationNode("and", left, right) =>
        List(
          convertSelector(input, left),
          convertSelector(input, right)
        ).sequence.map(_.flatten)
      case AST.BinaryOperationNode("or", left, right) =>
        for {
          leftConverted <- convertSelector(input, left)
          rightConverted <- convertSelector(input, right)
        } yield {
          List(Condition.Or(
            combineWithAnd(leftConverted),
            combineWithAnd(rightConverted)
          ))
        }
      case AST.BinaryOperationNode("=", left, right) =>
        for {
          leftExpression <- ExpressionBuilder.convertExpression(input, left)
          rightExpression <- ExpressionBuilder.convertExpression(input, right)
          eq <- buildEquals(leftExpression, rightExpression)
        } yield List(eq)
      case AST.UnaryOperationNode("not", sub) =>
        convertSelector(input, sub).map { subElement =>
          List(Condition.Not(combineWithAnd(subElement)))
        }
      case AST.BinaryOperationNode(op, left, right) if op == "<>" || op == "!=" =>
        for {
          leftExpression <- ExpressionBuilder.convertExpression(input, left)
          rightExpression <- ExpressionBuilder.convertExpression(input, right)
          eq <- buildEquals(leftExpression, rightExpression)
        } yield List(Condition.Not(
          eq
        ))
      case b: AST.BinaryOperationNode =>
        ExpressionBuilder.convertExpression(input, b).flatMap {
          case c: Condition                            => Right(List(c))
          case n: Expression if n.dataType == BoolType => Right(List(WrappedExpression(n)))
          case other =>
            Left(s"Could not convert ${b} to condition as it doesn't emit boolean")
        }
      case AST.BoolNode(true) =>
        // special case, empty
        Right(Nil)
      case AST.BoolNode(false) =>
        // Will always be empty, should be optimized away
        Right(List(Condition.WrappedExpression(ConstantExpression(Bundle.fundamental(false)))))
      case other =>
        Left(s"Expression not yet supported ${other}")
    }
  }

  private def combineWithAnd(expressions: List[Condition]): Condition = {
    expressions match {
      case Nil       => Condition.WrappedExpression(ConstantExpression(Bundle.fundamental(true)))
      case List(one) => one
      case multiples =>
        multiples.reduce(Condition.And(_, _))
    }
  }

  private def buildEquals(left: Expression, right: Expression): Either[String, Condition] = {
    for {
      commonType <- CastBuilder.comparisonType(left, right)
      leftWrapped <- CastBuilder.wrapType(left, commonType)
      rightWrapped <- CastBuilder.wrapType(right, commonType)
    } yield {
      Condition.Equals(leftWrapped, rightWrapped)
    }
  }

}

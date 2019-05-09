package ai.mantik.planner.select.builder

import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.planner.select.parser.AST
import ai.mantik.planner.select.{ BinaryExpression, ColumnExpression, Condition, Expression }

/** Convert AST Expressions into Expressions. */
private[builder] object ExpressionBuilder {

  /** Convert an AST Expression into a Expression. */
  def convertExpression(input: TabularData, node: AST.ExpressionNode): Either[String, Expression] = {
    node match {
      case identifierNode: AST.IdentifierNode => findColumn(input, identifierNode)
      case castNode: AST.CastNode =>
        for {
          expression <- convertExpression(input, castNode.expression)
          cast <- CastBuilder.buildCast(expression, castNode)
        } yield cast
      case c: AST.ConstantExpressionNode =>
        ConstantBuilder.convertConstant(c)
      case unary: AST.UnaryOperationNode =>
        unary.operation match {
          case "not" =>
            convertExpression(input, unary.exp).flatMap { exp =>
              exp.dataType match {
                case FundamentalType.BoolType =>
                  Right(Condition.Not(Condition.WrappedExpression(exp)))
                case other =>
                  Left("Cannot negate a non boolean data type")
              }
            }
          case other =>
            Left(s"Unsupported unary operation ${other}")
        }
      case b: AST.BinaryOperationNode =>
        for {
          op <- convertBinaryOperation(b.operation)
          left <- convertExpression(input, b.left)
          right <- convertExpression(input, b.right)
          commonType <- CastBuilder.operationType(op, left, right)
          leftCasted <- CastBuilder.wrapType(left, commonType)
          rightCasted <- CastBuilder.wrapType(right, commonType)
        } yield BinaryExpression(op, leftCasted, rightCasted)
    }
  }

  private def convertBinaryOperation(op: String): Either[String, BinaryOperation] = {
    opMap.get(op) match {
      case None     => Left(s"Operation ${op} not yet supported")
      case Some(op) => Right(op)
    }
  }

  private val opMap = Map(
    "+" -> BinaryOperation.Add,
    "-" -> BinaryOperation.Sub,
    "*" -> BinaryOperation.Mul,
    "/" -> BinaryOperation.Div
  )

  private def findColumn(input: TabularData, identifier: AST.IdentifierNode): Either[String, ColumnExpression] = {
    input.columns.zipWithIndex.find {
      case ((columnName, _), _) =>
        nameMatch(columnName, identifier)
    } match {
      case None => Left(s"Column ${identifier} not found")
      case Some(((_, columnType), columnId)) =>
        Right(ColumnExpression(columnId, columnType))
    }
  }

  private def nameMatch(columnName: String, identifier: AST.IdentifierNode): Boolean = {
    if (identifier.ignoreCase) {
      columnName.toLowerCase == identifier.name.toLowerCase
    } else {
      columnName == identifier.name
    }
  }
}

package ai.mantik.planner.select

import ai.mantik.ds.{ DataType, FundamentalType }
import ai.mantik.ds.element.Bundle

/** A expression (e.g. Inside Select). */
sealed trait Expression {
  def dataType: DataType
}

/** A Constant Expression. */
case class ConstantExpression(
    value: Bundle
) extends Expression {
  override def dataType: DataType = value.model
}

/** A Column is selected. */
case class ColumnExpression(
    columnId: Int,
    dataType: DataType
) extends Expression

/** An expression is casted. */
case class CastExpression(
    expression: Expression,
    dataType: DataType
) extends Expression

/** A Simple binary expression which works on the same type. */
case class BinaryExpression(
    op: BinaryOp,
    left: Expression,
    right: Expression
) extends Expression {
  require(left.dataType == right.dataType)
  override def dataType: DataType = left.dataType
}

/** A Condition is a special expression which emits a boolean value. */
sealed trait Condition extends Expression {
  override def dataType: DataType = FundamentalType.BoolType
}

object Condition {
  case class WrappedExpression(expression: Expression) extends Condition {
    require(expression.dataType == FundamentalType.BoolType)
  }

  case class Equals(left: Expression, right: Expression) extends Condition

  case class Not(predicate: Condition) extends Condition

  case class And(left: Expression, right: Expression) extends Condition

  case class Or(left: Expression, right: Expression) extends Condition
}

sealed trait BinaryOp

object BinaryOp {
  case object Add extends BinaryOp
  case object Mul extends BinaryOp
  case object Div extends BinaryOp
  case object Sub extends BinaryOp
}
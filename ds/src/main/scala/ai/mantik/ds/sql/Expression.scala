package ai.mantik.ds.sql

import ai.mantik.ds.element.{ Bundle, SingleElementBundle, ValueEncoder }
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.{ DataType, FundamentalType }

/** A expression (e.g. Inside Select). */
sealed trait Expression {
  def dataType: DataType
}

/** A Constant Expression. */
case class ConstantExpression(
    value: SingleElementBundle
) extends Expression {
  override def dataType: DataType = value.model
}

object ConstantExpression {
  def apply[T: ValueEncoder](x: T): ConstantExpression = {
    ConstantExpression(Bundle.fundamental(x))
  }
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
    op: BinaryOperation,
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

  case class IsNull(expression: Expression) extends Condition
}

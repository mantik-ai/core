package ai.mantik.ds.sql

import ai.mantik.ds.element.{ Bundle, SingleElementBundle, ValueEncoder }
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.{ DataType, FundamentalType }

/** An expression (e.g. Inside Select). */
sealed trait Expression {
  def dataType: DataType

  /** Returns direct dependencies. */
  def dependencies: List[Expression]

  /** Convert this Expression to a condition. */
  def asCondition: Option[Condition] = {
    this match {
      case c: Condition => Some(c)
      case e if e.dataType == FundamentalType.BoolType => Some(Condition.WrappedExpression(e))
      case _ => None
    }
  }
}

/** An expression which is not build from other expressions*/
sealed trait LeafExpression extends Expression {
  override final def dependencies: List[Expression] = Nil
}

/** An expression built from one expression. */
sealed trait UnaryExpression extends Expression {
  def expression: Expression

  override final def dependencies: List[Expression] = List(expression)
}

/** An expression built from two expressions */
sealed trait BinaryExpression extends Expression {
  def left: Expression
  def right: Expression

  override final def dependencies: List[Expression] = List(left, right)
}

/** A Constant Expression. */
case class ConstantExpression(
    value: SingleElementBundle
) extends LeafExpression {
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
) extends LeafExpression

/** An expression is casted. */
case class CastExpression(
    expression: Expression,
    dataType: DataType
) extends UnaryExpression

/** A Simple binary expression which works on the same type. */
case class BinaryOperationExpression(
    op: BinaryOperation,
    left: Expression,
    right: Expression
) extends BinaryExpression {
  require(left.dataType == right.dataType)
  override def dataType: DataType = left.dataType
}

/** A Condition is a special expression which emits a boolean value. */
sealed trait Condition extends Expression {
  override def dataType: DataType = FundamentalType.BoolType
}

object Condition {
  case class WrappedExpression(expression: Expression) extends Condition with UnaryExpression {
    require(expression.dataType == FundamentalType.BoolType)
  }

  case class Equals(left: Expression, right: Expression) extends Condition with BinaryExpression

  case class Not(predicate: Condition) extends Condition with UnaryExpression {
    def expression: Expression = predicate
  }

  case class And(left: Condition, right: Condition) extends Condition with BinaryExpression

  case class Or(left: Condition, right: Condition) extends Condition with BinaryExpression

  case class IsNull(expression: Expression) extends Condition with UnaryExpression
}

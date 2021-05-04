/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.ds.sql

import ai.mantik.ds.element.{Bundle, SingleElementBundle, ValueEncoder}
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.{ArrayT, DataType, FundamentalType, Nullable, Struct}

/** An expression (e.g. Inside Select). */
sealed trait Expression {
  def dataType: DataType

  /** Returns direct dependencies. */
  def dependencies: List[Expression]

  /** Convert this Expression to a condition. */
  def asCondition: Option[Condition] = {
    this match {
      case c: Condition                                => Some(c)
      case e if e.dataType == FundamentalType.BoolType => Some(Condition.WrappedExpression(e))
      case _                                           => None
    }
  }
}

/** An expression which is not build from other expressions */
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

/** Returns array length. */
case class SizeExpression(
    expression: Expression
) extends UnaryExpression {
  override def dataType: DataType = Nullable.mapIfNullable(expression.dataType, _ => FundamentalType.Int32)
}

/** Returns array value. */
case class ArrayGetExpression(
    array: Expression,
    index: Expression
) extends BinaryExpression {
  require(Nullable.unwrap(array.dataType).isInstanceOf[ArrayT])
  require(Nullable.unwrap(index.dataType) == FundamentalType.Int32)

  override def left: Expression = array

  override def right: Expression = index

  override def dataType: DataType = Nullable.makeNullable(
    Nullable.unwrap(array.dataType).asInstanceOf[ArrayT].underlying
  )
}

/** Gives access to a structural field */
case class StructAccessExpression(
    expression: Expression,
    name: String
) extends UnaryExpression {

  def underlyingStruct: Struct = Nullable.unwrap(expression.dataType) match {
    case s: Struct => s
    case other     => throw new IllegalStateException(s"Expected struct, got ${other}")
  }

  def underlyingField: DataType =
    underlyingStruct.fields.getOrElse(name, throw new IllegalStateException(s"Field ${name} not found"))

  override def dataType: DataType = {
    Nullable.mapIfNullable(expression.dataType, _ => underlyingField)
  }
}

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

  /** A Bool value as Condition. */
  def boolValue(x: Boolean): Condition = WrappedExpression(
    ConstantExpression(x)
  )
}

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
package ai.mantik.ds.sql.builder

import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.sql.Condition.IsNull
import ai.mantik.ds.sql.{
  ArrayGetExpression,
  BinaryExpression,
  BinaryOperationExpression,
  ColumnExpression,
  Condition,
  Expression,
  QueryTabularType,
  SizeExpression,
  StructAccessExpression
}
import ai.mantik.ds.{DataType, FundamentalType, TabularData}
import ai.mantik.ds.sql.parser.AST
import ai.mantik.ds.sql.parser.AST.NullNode

/** Convert AST Expressions into Expressions. */
private[sql] object ExpressionBuilder {

  /** Convert an AST Expression into a Expression. */
  def convertExpression(input: QueryTabularType, node: AST.ExpressionNode): Either[String, Expression] = {
    node match {
      case identifierNode: AST.IdentifierNode => buildColumnExpressionByIdentifier(input, identifierNode)
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
          case "size" =>
            for {
              exp <- convertExpression(input, unary.exp)
              expArray <- CastBuilder.ensureArray(exp, true)
            } yield {
              SizeExpression(expArray._1)
            }
          case other =>
            Left(s"Unsupported unary operation ${other}")
        }
      case b: AST.BinaryOperationNode if b.operation == "is" =>
        if (b.right == NullNode) {
          convertExpression(input, b.left).map { left =>
            Condition.IsNull(left)
          }
        } else {
          Left(s"Only IS <NULL> supported, got ${b.right}")
        }
      case b: AST.BinaryOperationNode if b.operation == "isnot" =>
        if (b.right == NullNode) {
          convertExpression(input, b.left).map { left =>
            Condition.Not(Condition.IsNull(left))
          }
        } else {
          Left(s"Only IS NOT <NULL> supported, got ${b.right}")
        }
      case b: AST.BinaryOperationNode if b.operation == "[]" =>
        for {
          left <- convertExpression(input, b.left)
          leftArray <- CastBuilder.ensureArray(left, true)
          right <- convertExpression(input, b.right)
          rightIndex <- CastBuilder.wrapTypeWithNullableSupport(right, FundamentalType.Int32)
        } yield {
          ArrayGetExpression(leftArray._1, rightIndex)
        }
      case s: AST.StructAccessNode =>
        for {
          input <- convertExpression(input, s.expression)
          inputStructure <- CastBuilder.ensureStruct(input, true)
          structure = inputStructure._2
          _ <-
            if (structure.fields.contains(s.name)) {
              Right(())
            } else {
              Left(s"Structure ${structure} doesn't contain field ${s.name}")
            }
        } yield {
          StructAccessExpression(inputStructure._1, s.name)
        }
      case b: AST.BinaryOperationNode if binaryConditions.contains(b.operation) =>
        for {
          left <- convertExpression(input, b.left)
          right <- convertExpression(input, b.right)
          op <- convertBinaryCondition(b.operation, left, right)
        } yield op
      case b: AST.BinaryOperationNode =>
        for {
          op <- convertBinaryOperation(b.operation)
          left <- convertExpression(input, b.left)
          right <- convertExpression(input, b.right)
          commonType <- CastBuilder.operationType(op, left, right)
          leftCasted <- CastBuilder.wrapType(left, commonType)
          rightCasted <- CastBuilder.wrapType(right, commonType)
        } yield BinaryOperationExpression(op, leftCasted, rightCasted)
    }
  }

  private val binaryConditions: Seq[String] = Seq("=", "and", "or")
  private def convertBinaryCondition(
      op: String,
      left: Expression,
      right: Expression
  ): Either[String, Condition with BinaryExpression] = {
    def asCondition(e: Expression): Either[String, Condition] = {
      e.asCondition match {
        case None     => Left(s"Expected condition got ${e}")
        case Some(ok) => Right(ok)
      }
    }
    op match {
      case "=" =>
        for {
          commonType <- CastBuilder.comparisonType(left, right)
          leftCasted <- CastBuilder.wrapType(left, commonType)
          rightCasted <- CastBuilder.wrapType(right, commonType)
        } yield Condition.Equals(leftCasted, rightCasted)
      case "and" =>
        for {
          leftc <- asCondition(left)
          rightc <- asCondition(right)
        } yield Condition.And(leftc, rightc)
      case "or" =>
        for {
          leftc <- asCondition(left)
          rightc <- asCondition(right)
        } yield Condition.Or(leftc, rightc)
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

  def buildColumnExpressionByIdentifier(
      input: QueryTabularType,
      identifier: AST.IdentifierNode
  ): Either[String, ColumnExpression] = {
    findColumnByIdentifier(input, identifier).map { case (columnId, dataType) =>
      ColumnExpression(columnId, dataType)
    }
  }

  /** Find a column by identifier */
  def findColumnByIdentifier(
      input: QueryTabularType,
      identifier: AST.IdentifierNode
  ): Either[String, (Int, DataType)] = {
    input.lookupColumn(identifier.name, caseSensitive = !identifier.ignoreCase).map { x => (x._1, x._2.dataType) }
  }
}

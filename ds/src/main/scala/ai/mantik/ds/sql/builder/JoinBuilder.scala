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

import ai.mantik.ds.TabularData
import ai.mantik.ds.sql._
import ai.mantik.ds.sql.parser.AST
import cats.implicits._

private[sql] object JoinBuilder {

  def buildJoinFromParsed(parsed: AST.JoinNode)(implicit sqlContext: SqlContext): Either[String, Join] = {
    for {
      leftQuery <- QueryBuilder.buildQueryFromParsed(parsed.left)
      rightQuery <- QueryBuilder.buildQueryFromParsed(parsed.right)
      joinType = convertJoinType(parsed.joinType)
      innerModel = innerTabularData(leftQuery, rightQuery, joinType)
      condition <- buildJoinCondition(leftQuery, rightQuery, joinType, innerModel, parsed.condition)
    } yield Join(leftQuery, rightQuery, joinType, condition)
  }

  def buildAnonymousUsing(
      left: TabularData,
      right: TabularData,
      using: Seq[String],
      joinType: JoinType
  ): Either[String, Join] = {
    val condition = AST.JoinCondition.Using(
      using.map { name =>
        AST.IdentifierNode(name)
      }.toVector
    )
    val leftQuery = AnonymousInput(left, 0)
    val rightQuery = AnonymousInput(right, 1)
    buildUsingCondition(leftQuery, rightQuery, joinType, condition).map { condition =>
      Join(leftQuery, rightQuery, joinType = joinType, condition = condition)
    }
  }

  private def convertJoinType(joinType: AST.JoinType): JoinType = {
    joinType match {
      case AST.JoinType.Inner => JoinType.Inner
      case AST.JoinType.Left  => JoinType.Left
      case AST.JoinType.Right => JoinType.Right
      case AST.JoinType.Outer => JoinType.Outer
    }
  }

  /** Determine column names and data types as being used during JOIN Expressions */
  def innerTabularData(left: Query, right: Query, joinType: JoinType): QueryTabularType = {
    val leftMaybeNullable = if (joinType == JoinType.Right || joinType == JoinType.Outer) {
      left.resultingQueryType.makeNullable
    } else {
      left.resultingQueryType
    }

    val rightMaybeNullable = if (joinType == JoinType.Left || joinType == JoinType.Outer) {
      right.resultingQueryType.makeNullable
    } else {
      right.resultingQueryType
    }
    val combined = leftMaybeNullable ++ rightMaybeNullable
    val shadowed = if (joinType == JoinType.Right) {
      combined.shadow(false)
    } else {
      combined.shadow(true)
    }
    shadowed
  }

  private def buildJoinCondition(
      left: Query,
      right: Query,
      joinType: JoinType,
      innerModel: QueryTabularType,
      condition: AST.JoinCondition
  ): Either[String, JoinCondition] = {
    condition match {
      case AST.JoinCondition.Cross        => Right(JoinCondition.Cross)
      case on: AST.JoinCondition.On       => buildOnCondition(innerModel, on)
      case using: AST.JoinCondition.Using => buildUsingCondition(left, right, joinType, using)
    }

  }

  private def buildOnCondition(
      innerModel: QueryTabularType,
      condition: AST.JoinCondition.On
  ): Either[String, JoinCondition.On] = {
    for {
      expression <- ExpressionBuilder.convertExpression(innerModel, condition.expression)
      asCondition <- extractCondition(expression, innerModel)
    } yield JoinCondition.On(asCondition)
  }

  private def buildUsingCondition(
      left: Query,
      right: Query,
      joinType: JoinType,
      condition: AST.JoinCondition.Using
  ): Either[String, JoinCondition.Using] = {
    condition.columns
      .map { identifierNode =>
        for {
          leftLookup <- ExpressionBuilder.findColumnByIdentifier(left.resultingQueryType, identifierNode)
          leftId = leftLookup._1
          leftDataType = leftLookup._2
          rightLookup <- ExpressionBuilder.findColumnByIdentifier(right.resultingQueryType, identifierNode)
          rightId = rightLookup._1
          rightDataType = rightLookup._2
          comparisonType <- CastBuilder.comparisonType(leftDataType, rightDataType)
        } yield {
          val dropId = if (joinType == JoinType.Right) {
            leftId
          } else {
            rightId + left.resultingQueryType.columns.size
          }
          JoinCondition.UsingColumn(
            identifierNode.name,
            caseSensitive = !identifierNode.ignoreCase,
            leftId = leftId,
            rightId = rightId,
            dropId = dropId,
            dataType = comparisonType
          )
        }
      }
      .sequence
      .map(JoinCondition.Using.apply)
  }

  private def extractCondition(expression: Expression, innerModel: QueryTabularType): Either[String, Condition] = {
    expression.asCondition match {
      case Some(c) => Right(c)
      case None =>
        val formatted = new SqlFormatter(innerModel).formatExpression(expression)
        Left(s"Expected condition, got ${formatted}")
    }
  }
}

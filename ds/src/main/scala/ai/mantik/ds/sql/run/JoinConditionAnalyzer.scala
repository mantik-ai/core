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
package ai.mantik.ds.sql.run

import ai.mantik.ds.{DataType, Nullable}
import ai.mantik.ds.sql.builder.CastBuilder
import ai.mantik.ds.sql.{
  ColumnExpression,
  Condition,
  Expression,
  ExpressionTransformation,
  Join,
  JoinCondition,
  JoinType
}
import cats.implicits._

import scala.collection.BitSet

/** Helper for analyzing join conditions. */
private[run] object JoinConditionAnalyzer {

  /** A Single comparison column */
  case class Comparison(
      dataType: DataType,
      leftExpression: Expression,
      rightExpression: Expression,
      columnIds: Option[(Int, Int)]
  )

  /**
    * Analysis result.
    * @param comparisons on the left and the right side
    * @param filter optional filter which works on the inner value.
    */
  case class Analysis(
      comparisons: Vector[Comparison] = Vector.empty,
      filter: Option[Condition] = None
  ) {

    /** A Program for generating groups on the left side */
    def leftGrouper: Either[String, Program] = {
      compileExpressionList(comparisons.map(_.leftExpression))
    }

    /** A Program for generating groups on the right side */
    def rightGrouper: Either[String, Program] = {
      compileExpressionList(comparisons.map(_.rightExpression))
    }

    def groupSize: Int = comparisons.size

    def groupTypes: Vector[DataType] = comparisons.map(_.dataType)

    def filterProgram: Either[String, Option[Program]] = {
      filter match {
        case None => Right(None)
        case Some(present) =>
          Compiler.compileCondition(present).map { opCodes =>
            Some(Program.fromOps(opCodes))
          }
      }
    }

    private def compileExpressionList(expressions: Vector[Expression]): Either[String, Program] = {
      expressions
        .map { expression =>
          Compiler.compileExpression(expression)
        }
        .sequence
        .map { opCodeVector =>
          Program.fromOps(opCodeVector.flatten)
        }
    }
  }

  def analyze(join: Join): Either[String, Analysis] = {
    join.condition match {
      case JoinCondition.Cross => Right(Analysis())
      case JoinCondition.Using(columns) =>
        analyzeUsing(join, columns)
      case JoinCondition.On(on) =>
        analyzeOn(join, on)
    }
  }

  /** Analyze USING-Condition. */
  private def analyzeUsing(join: Join, columns: Vector[JoinCondition.UsingColumn]): Either[String, Analysis] = {
    columns
      .map { column =>
        // Workaround: Our FULL OUTER join expects nullable columns everywhere
        // but if there are two non-nullable it would result into a non-nullable
        // result column
        // This is not great
        val forceNullability = join.joinType == JoinType.Outer

        for {
          left <- join.left.resultingQueryType.lookupColumn(column.name, column.caseSensitive)
          right <- join.right.resultingQueryType.lookupColumn(column.name, column.caseSensitive)
          leftExp = ColumnExpression(left._1, left._2.dataType)
          rightExp = ColumnExpression(right._1, right._2.dataType)
          commonType <- CastBuilder.comparisonType(leftExp, rightExp)
          commonTypeMaybeNullable =
            if (forceNullability) {
              Nullable.makeNullable(commonType)
            } else {
              commonType
            }
          leftCasted <- CastBuilder.wrapType(leftExp, commonTypeMaybeNullable)
          rightCasted <- CastBuilder.wrapType(rightExp, commonTypeMaybeNullable)
        } yield {
          Comparison(commonTypeMaybeNullable, leftCasted, rightCasted, Some(left._1, right._1))
        }
      }
      .sequence
      .map { triples =>
        Analysis(
          comparisons = triples,
          filter = None
        )
      }
  }

  /** Analyze ON-Condition */
  def analyzeOn(join: Join, on: Condition): Either[String, Analysis] = {
    normalForm(on).map { conditions =>
      // Split conditions into ones which we can use as left/right selector and those who have to applied later
      val splitted: Vector[Either[Comparison, Condition]] = conditions.map { condition =>
        extractLeftRightCondition(join, condition) match {
          case Some((left, right)) => Left(Comparison(left.dataType, left, right, None))
          case None                => Right(condition)
        }
      }

      val comparisons = splitted.collect { case Left(x) =>
        x
      }

      val filter = splitted.collect { case Right(filterCondition) =>
        filterCondition
      }
      val combinedFilter = filter.size match {
        case 0 => None
        case 1 => Some(filter.head)
        case n => Some(filter.tail.foldLeft(filter.head) { case (c, n) => Condition.And(c, n) })
      }

      Analysis(
        comparisons,
        combinedFilter
      )
    }
  }

  /**
    * Extract comparisonExpressions from a condition, if possible.
    * @return comparison expression for the left and for the right side
    */
  private def extractLeftRightCondition(join: Join, condition: Condition): Option[(Expression, Expression)] = {

    /** Returns the dependencies of an expression */
    def dependencies(expression: Expression): BitSet = {
      ExpressionTransformation.foldTree(expression)(BitSet.empty) {
        case (current, ColumnExpression(id, _)) => current.union(BitSet(id))
        case (current, _)                       => current
      }
    }

    val leftSize = join.left.resultingTabularType.columns.size

    def isFromLeftQuery(dependencyId: Int): Boolean = {
      dependencyId < leftSize
    }

    /**
      * Translates an expression so that it's looking for input of the right side only
      * (columnIds are shifted)
      */
    def translateToRightPerspective(expression: Expression): Expression = {
      ExpressionTransformation.deepMap(expression) {
        case c: ColumnExpression => c.copy(columnId = c.columnId - leftSize)
        case otherwise           => otherwise
      }
    }

    condition match {
      case Condition.Equals(leftEq, rightEq) =>
        // Note: leftEq and rightEq mean the sides of the comparison
        // if they completely match leftQuery and rightQuery will be checked now

        val leftEqDependencies = dependencies(leftEq)
        val rightEqDependencies = dependencies(rightEq)

        val leftEqIsFromLeftQueryOnly = leftEqDependencies.forall(isFromLeftQuery)
        val leftEqIsFromRightQueryOnly = leftEqDependencies.forall(x => !isFromLeftQuery(x))
        val rightEqIsFromLeftQueryOnly = rightEqDependencies.forall(isFromLeftQuery)
        val rightEqIsFromRightQueryOnly = rightEqDependencies.forall(x => !isFromLeftQuery(x))

        if (leftEqIsFromLeftQueryOnly && rightEqIsFromRightQueryOnly) {
          Some(leftEq -> translateToRightPerspective(rightEq))
        } else if (leftEqIsFromRightQueryOnly && rightEqIsFromLeftQueryOnly) {
          Some(rightEq -> translateToRightPerspective(leftEq))
        } else {
          None
        }
      case _ =>
        None
    }
  }

  private def normalForm(expression: Condition): Either[String, Vector[Condition]] = {
    // Note: here is also room for optimizations
    expression match {
      case Condition.And(left, right) =>
        for {
          leftNf <- normalForm(left)
          rightNf <- normalForm(right)
        } yield (leftNf ++ rightNf)
      case x if x == Condition.boolValue(true) =>
        Right(Vector.empty)
      case somethingElse =>
        Right(Vector(somethingElse))
    }
  }

}

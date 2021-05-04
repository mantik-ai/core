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

import ai.mantik.ds.converter.Cast
import ai.mantik.ds.element.{Bundle, NullElement, SingleElementBundle}
import ai.mantik.ds.{DataType, Nullable, TabularData}
import cats.implicits._

/** Helpers for creating automatic union operations with data type compatibility */
object AutoUnion {

  def autoUnion(left: DataType, right: DataType, all: Boolean): Either[String, Union] = {
    for {
      leftTabular <- AutoSelect.fetchTabular(left)
      rightTabular <- AutoSelect.fetchTabular(right)
      autoUnion <- autoUnion(leftTabular, rightTabular, all)
    } yield autoUnion
  }

  def autoUnion(left: TabularData, right: TabularData, all: Boolean): Either[String, Union] = {
    if (left == right) {
      return Right(
        Union(
          AnonymousInput(left),
          AnonymousInput(right, 1),
          all
        )
      )
    }

    val columnNames = (left.columns.keys.toVector ++ right.columns.keys.toVector).distinct

    val maybeCommonTypes: Either[String, Vector[DataType]] = columnNames.map { columnName =>
      val leftDataType = left.columns.get(columnName)
      val rightDataType = right.columns.get(columnName)
      findCommonType(leftDataType, rightDataType) match {
        case None => Left(s"Could not find a common type for ${leftDataType}/${rightDataType}")
        case Some(commonType) =>
          Right(commonType)
      }
    }.sequence

    maybeCommonTypes.map { commonTypes =>
      val leftSelector = buildSelectors(columnNames, left, commonTypes)
      val rightSelectors = buildSelectors(columnNames, right, commonTypes)

      Union(
        Select(
          AnonymousInput(left, 0),
          Some(leftSelector)
        ),
        Select(
          AnonymousInput(right, 1),
          Some(rightSelectors)
        ),
        all = all
      )
    }
  }

  private def findCommonType(left: Option[DataType], right: Option[DataType]): Option[DataType] = {
    (left, right) match {
      case (None, None)        => None
      case (None, Some(right)) => Some(Nullable.makeNullable(right))
      case (Some(left), None)  => Some(Nullable.makeNullable(left))
      case (Some(left), Some(right)) if left == right =>
        Some(left)
      case (Some(left), Some(right)) =>
        Cast.findCast(left, right) match {
          case Right(cast) if cast.isSafe => Some(right)
          case _ =>
            Cast.findCast(right, left) match {
              case Right(cast) if cast.isSafe => Some(left)
              case _                          => None
            }
        }
    }
  }

  private def buildSelectors(
      columnNames: Vector[String],
      from: TabularData,
      commonTypes: Vector[DataType]
  ): Vector[SelectProjection] = {
    columnNames.zip(commonTypes).map { case (columnName, commonType) =>
      val fromIdType = for {
        fromId <- from.lookupColumnIndex(columnName)
        fromType <- from.columns.get(columnName)
      } yield (fromId -> fromType)
      SelectProjection(
        columnName,
        buildExpression(fromIdType, commonType)
      )
    }
  }

  private def buildExpression(from: Option[(Int, DataType)], to: DataType): Expression = {
    from match {
      case None =>
        assume(to.isNullable)
        CastExpression(
          ConstantExpression(Bundle.voidNull),
          to
        )
      case Some((id, dataType)) if dataType == to =>
        ColumnExpression(id, dataType)
      case Some((id, dataType)) =>
        CastExpression(
          ColumnExpression(id, dataType),
          to
        )
    }
  }
}

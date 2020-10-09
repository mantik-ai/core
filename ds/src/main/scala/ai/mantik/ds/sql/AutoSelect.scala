package ai.mantik.ds.sql

import ai.mantik.ds.converter.Cast
import ai.mantik.ds.{ DataType, TabularData }
import cats.implicits._

/** Helpers for creating automatic Select-Operations for converting DataTypes. */
object AutoSelect {

  /** Automatically generates a select statement for converting data types. */
  def autoSelect(from: DataType, expected: DataType): Either[String, Select] = {
    for {
      fromTabular <- fetchTabular(from)
      toTabular <- fetchTabular(expected)
      autoSelect <- autoSelect(fromTabular, toTabular)
    } yield autoSelect
  }

  private[sql] def fetchTabular(dt: DataType): Either[String, TabularData] = {
    dt match {
      case t: TabularData => Right(t)
      case _              => Left("Can only auto adapt tabular data")
    }
  }

  /** Generates a select statement from a from tabular to a target tabular data type */
  def autoSelect(from: TabularData, to: TabularData): Either[String, Select] = {
    buildColumnMapping(from, to).flatMap { columnMapping =>
      val maybeProjections = to.columns.map {
        case (toColumn, targetType) =>
          val fromColumn = columnMapping(toColumn)
          buildColumnSelector(from, fromColumn, toColumn, targetType)
      }.toList.sequence

      maybeProjections.map { projections =>
        Select(AnonymousInput(from), projections = Some(projections))
      }
    }
  }

  /**
   * Tries to retrieve a column mapping from from to to.
   *
   * @return Map, keys column names from 'to', values column names from 'from'
   */
  private def buildColumnMapping(from: TabularData, to: TabularData): Either[String, Map[String, String]] = {
    val fromColumnNames = from.columns.keys.toList
    val toColumnNames = to.columns.keys.toList

    val resolved = fromColumnNames.intersect(toColumnNames).map { v => v -> v }.toMap
    val missing = toColumnNames.diff(fromColumnNames)

    val singleMissing = missing match {
      case Nil          => return Right(resolved)
      case List(single) => single
      case multiples    => return Left(s"Could not resolve ${multiples}")
    }

    val unresolved = fromColumnNames.diff(toColumnNames)
    if (unresolved.size == 1) {
      return Right(resolved + (singleMissing -> unresolved.head))
    }
    Left(s"Could not resolve ${singleMissing}")
  }

  private def buildColumnSelector(from: TabularData, fromColumn: String, targetColumn: String, expectedType: DataType): Either[String, SelectProjection] = {
    val fromIndex = from.lookupColumnIndex(fromColumn).getOrElse {
      // Column names should be already checked, so this should not happen
      throw new IllegalArgumentException(s"Column ${fromColumn} not found")
    }
    val fromType = from.columns(fromColumn)
    Cast.findCast(fromType, expectedType).flatMap {
      case c: Cast if c.canFail => Left(s"Cast from ${fromColumn} can fail, cannot be auto converted")
      case c: Cast if c.loosing => Left(s"Cast from ${fromColumn} can loose precision, cannot be autoconverted")
      case c: Cast if c.isIdentity =>
        Right(SelectProjection(targetColumn, ColumnExpression(fromIndex, fromType)))
      case c: Cast =>
        Right(SelectProjection(targetColumn, CastExpression(
          ColumnExpression(fromIndex, fromType),
          expectedType
        )))
    }
  }
}

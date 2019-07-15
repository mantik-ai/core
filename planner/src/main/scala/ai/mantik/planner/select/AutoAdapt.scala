package ai.mantik.planner.select

import ai.mantik.ds.converter.Cast
import ai.mantik.ds.{ DataType, TabularData }
import ai.mantik.planner.{ Algorithm, DataSet }

/** Automatically generates select based adapters for [[DataSet]] */
object AutoAdapt {

  /** Automatically adapts a dataset to the expected type. */
  def autoAdapt(from: DataSet, expected: DataType): Either[String, DataSet] = {
    if (from.dataType == expected) {
      return Right(from)
    }
    autoSelect(from.dataType, expected).map { select =>
      from.select(select)
    }
  }

  /** Automatically generates a select statement for converting data types. */
  def autoSelect(from: DataType, expected: DataType): Either[String, Select] = {
    val fromTabular = from match {
      case t: TabularData => t
      case _              => return Left("Can only auto adapt tabular data")
    }
    val toTabular = expected match {
      case t: TabularData => t
      case _              => return Left("Can only auto adapt tabular data")
    }
    autoSelect(fromTabular, toTabular)
  }

  /** Automatically generates a select algorithm, converting data types. */
  def autoSelectAlgorithm(from: DataType, expected: DataType): Either[String, Algorithm] = {
    autoSelect(from, expected).map(Algorithm.fromSelect)
  }

  /** Generates a select statement from a from tabular to a target tabular data type */
  def autoSelect(from: TabularData, to: TabularData): Either[String, Select] = {
    buildColumnMapping(from, to).flatMap { columnMapping =>
      val maybeProjections = Utils.flatEither(to.columns.map {
        case (toColumn, targetType) =>
          val fromColumn = columnMapping(toColumn)
          buildColumnSelector(from, fromColumn, toColumn, targetType)
      }.toList)

      maybeProjections.map { projections =>
        Select(from, projections = Some(projections))
      }
    }
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

  /**
   * Tries to retrieve a column mapping from from to to.
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
}

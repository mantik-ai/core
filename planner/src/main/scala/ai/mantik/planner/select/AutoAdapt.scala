package ai.mantik.planner.select

import ai.mantik.ds.sql._
import ai.mantik.ds.{ DataType, TabularData }
import ai.mantik.planner.{ Algorithm, DataSet }

/** Automatically generates select based adapters for [[DataSet]] */
object AutoAdapt {

  /** Automatically adapts a dataset to the expected type. */
  def autoAdapt(from: DataSet, expected: DataType): Either[String, DataSet] = {
    if (from.dataType == expected) {
      return Right(from)
    }
    AutoSelect.autoSelect(from.dataType, expected).map { select =>
      from.select(select)
    }
  }

  /** Automatically generates a select algorithm, converting data types. */
  def autoSelectAlgorithm(from: DataType, expected: DataType): Either[String, Algorithm] = {
    AutoSelect.autoSelect(from, expected).map(Algorithm.fromSelect)
  }
}

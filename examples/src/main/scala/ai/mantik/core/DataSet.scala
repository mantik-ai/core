package ai.mantik.core

import ai.mantik.ds.DataType
import ai.mantik.ds.natural.NaturalBundle

/** Represents a DataSet. */
case class DataSet(
    source: Source,
    dataType: DataType
) extends MantikItem {

  def fetch: Action.FetchAction = Action.FetchAction(this)
}

object DataSet {

  def literal(bundle: NaturalBundle): DataSet = {
    DataSet(
      Source.Literal(bundle),
      bundle.model
    )
  }
}
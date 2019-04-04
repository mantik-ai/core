package ai.mantik.core

import ai.mantik.core.plugins.NaturalFormatPlugin
import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.repository.{ DataSetDefinition, Mantikfile }

/** Represents a DataSet. */
case class DataSet(
    source: Source,
    mantikfile: Mantikfile[DataSetDefinition]
) extends MantikItem {

  override type DefinitionType = DataSetDefinition

  def dataType: DataType = mantikfile.definition.`type`

  def fetch: Action.FetchAction = Action.FetchAction(this)
}

object DataSet {

  def literal(bundle: Bundle): DataSet = {
    natural(
      Source.BundleLiteral(bundle), bundle.model
    )
  }

  /** Creates a natural data source, with serialized data coming direct from a flow. */
  private[core] def natural(source: Source, dataType: DataType): DataSet = {
    DataSet(source, Mantikfile.pure(
      DataSetDefinition(
        format = NaturalFormatPlugin.format,
        `type` = dataType
      )
    ))
  }
}
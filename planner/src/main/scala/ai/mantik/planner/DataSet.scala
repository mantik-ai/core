package ai.mantik.planner

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.{DataType, TabularData}
import ai.mantik.ds.element.Bundle
import ai.mantik.planner.select.Select
import ai.mantik.repository.{DataSetDefinition, Mantikfile}

/** Represents a DataSet. */
case class DataSet(
    source: Source,
    mantikfile: Mantikfile[DataSetDefinition]
) extends MantikItem {

  override type DefinitionType = DataSetDefinition

  def dataType: DataType = mantikfile.definition.`type`

  def fetch: Action.FetchAction = Action.FetchAction(this)

  /**
    * Prepares a select statement on this dataset.
    *
    * @throws FeatureNotSupported if a select is applied on non tabular data or if the select could not be compiled.
    * @throws IllegalArgumentException on illegal selects.
    * */
  def select(statement: String): DataSet = {
    val tabularData = dataType match {
      case tabularData: TabularData => tabularData
      case other =>
        throw new FeatureNotSupported("Select statements are only supported for tabular data")
    }
    val select = Select.parse(tabularData, statement) match {
      case Left(error) => throw new IllegalArgumentException(s"Could not parse select ${error}")
      case Right(ok) => ok
    }
    val mantikFile = select.compileToSelectMantikfile() match {
      case Left(error) => throw new FeatureNotSupported(s"Could not compile select ${error}")
      case Right(ok) => ok
    }

    val source = Source.OperationResult(
      Operation.Application(
        Algorithm(
          Source.Empty,
          mantikFile
        ),
        this
      ),
    )
    DataSet.natural(source, select.resultingType)
  }
}

object DataSet {

  def literal(bundle: Bundle): DataSet = {
    natural(
      Source.BundleLiteral(bundle), bundle.model
    )
  }

  /** Creates a natural data source, with serialized data coming direct from a flow. */
  private[planner] def natural(source: Source, dataType: DataType): DataSet = {
    DataSet(source, Mantikfile.pure(
      DataSetDefinition(
        format = NaturalFormatName,
        `type` = dataType
      )
    ))
  }

  /** Name of the always-existing natural format. */
  val NaturalFormatName = "natural"
}
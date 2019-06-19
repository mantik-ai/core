package ai.mantik.planner

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.{DataType, TabularData}
import ai.mantik.ds.element.{Bundle, SingleElementBundle}
import ai.mantik.planner.select.{AutoAdapt, Select}
import ai.mantik.repository.{DataSetDefinition, Mantikfile}

/** A DataSet cannot be automatically converted to an expected type. */
class ConversionNotApplicableException(msg: String) extends IllegalArgumentException(msg)


/** Represents a DataSet. */
case class DataSet(
    source: Source,
    private [planner] val mantikfile: Mantikfile[DataSetDefinition]
) extends MantikItem {

  override type DefinitionType = DataSetDefinition
  override type OwnType = DataSet

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
    val parsedSelect = Select.parse(tabularData, statement) match {
      case Left(error) => throw new IllegalArgumentException(s"Could not parse select ${error}")
      case Right(ok) => ok
    }
    select(parsedSelect)
  }

  /** Derives a data set, as the result of applying a select to this dataset. */
  def select(select: Select): DataSet = {
    if (select.inputType != dataType) {
      throw new IllegalArgumentException("Select statement is for a different data type and not applicable")
    }
    val algorithm = Algorithm.fromSelect(select)

    val payloadSource = PayloadSource.OperationResult(
      Operation.Application(
        algorithm,
        this
      ),
    )
    DataSet.natural(Source.constructed(payloadSource), select.resultingType)
  }

  /** Tries to auto convert this data set to another data type.
    * Conversions can only be done if they do not loose precision or cannot fail single rows.
    * @throws ConversionNotApplicableException if the conversion can not be applied. */
  def autoAdaptOrFail(targetType: DataType): DataSet = {
    AutoAdapt.autoAdapt(this, targetType) match {
      case Left(msg) => throw new ConversionNotApplicableException(msg)
      case Right(adapted) => adapted
    }
  }

  /** Returns a dataset, which will be cached.
    * Note: caching is done lazy. */
  def cached: DataSet = {
    payloadSource match {
      case c: PayloadSource.Cached => this
      case _ =>
        val updatedSource = Source(
          DefinitionSource.Derived(source.definition),
          PayloadSource.Cached(source.payload)
        )
        new DataSet(updatedSource, mantikfile)
    }
  }

  override protected def withMantikfile(mantikfile: Mantikfile[DataSetDefinition]): DataSet = {
    DataSet(
      source.derive, mantikfile
    )
  }
}

object DataSet {

  def literal(bundle: Bundle): DataSet = {
    natural(
      Source.constructed(PayloadSource.BundleLiteral(bundle)), bundle.model
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
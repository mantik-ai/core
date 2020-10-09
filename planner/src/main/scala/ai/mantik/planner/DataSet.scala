package ai.mantik.planner

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.{ DataType, TabularData }
import ai.mantik.ds.element.{ Bundle, SingleElementBundle }
import ai.mantik.ds.sql.{ AutoSelect, AutoUnion, Select }
import ai.mantik.elements
import ai.mantik.elements.{ DataSetDefinition, ItemId, MantikHeader, NamedMantikId }
import ai.mantik.planner.repository.Bridge
import io.circe.{ Decoder, Encoder }

/** A DataSet cannot be automatically converted to an expected type. */
class ConversionNotApplicableException(msg: String) extends IllegalArgumentException(msg)

/** Represents a DataSet. */
case class DataSet(
    core: MantikItemCore[DataSetDefinition]
) extends BridgedMantikItem {

  override type DefinitionType = DataSetDefinition
  override type OwnType = DataSet

  def dataType: DataType = mantikHeader.definition.`type`

  def fetch: Action.FetchAction = Action.FetchAction(this)

  /**
   * Prepares a select statement on this dataset.
   *
   * @throws FeatureNotSupported if a select is applied on non tabular data or if the select could not be compiled.
   * @throws IllegalArgumentException on illegal selects.
   */
  def select(statement: String): DataSet = {
    val tabularData = dataType match {
      case tabularData: TabularData => tabularData
      case other =>
        throw new FeatureNotSupported("Select statements are only supported for tabular data")
    }
    val parsedSelect = Select.parse(tabularData, statement) match {
      case Left(error) => throw new IllegalArgumentException(s"Could not parse select ${error}")
      case Right(ok)   => ok
    }
    select(parsedSelect)
  }

  /** Derives a data set, as the result of applying a select to this dataset. */
  def select(select: Select): DataSet = {
    if (select.inputType != dataType) {
      throw new IllegalArgumentException("Select statement is for a different data type and not applicable")
    }

    val payloadSource = PayloadSource.OperationResult(
      Operation.SqlQueryOperation(
        select,
        Vector(this)
      )
    )
    DataSet.natural(Source.constructed(payloadSource), select.resultingType)
  }

  /**
   * Auto UNION this and another data set
   * @param other the other dataset
   * @param all if true generate UNION ALL, otherwise duplicates are omitted.
   */
  def autoUnion(other: DataSet, all: Boolean): DataSet = {
    val autoUnion = AutoUnion.autoUnion(this.dataType, other.dataType, all) match {
      case Left(error) => throw new IllegalArgumentException(s"Could not generate auto union ${error}")
      case Right(ok)   => ok
    }
    val payloadSource = PayloadSource.OperationResult(
      Operation.SqlQueryOperation(
        autoUnion,
        Vector(this, other)
      )
    )
    DataSet.natural(Source.constructed(payloadSource), autoUnion.resultingType)
  }

  /**
   * Tries to auto convert this data set to another data type.
   * Conversions can only be done if they do not loose precision or cannot fail single rows.
   * @throws ConversionNotApplicableException if the conversion can not be applied.
   */
  def autoAdaptOrFail(targetType: DataType): DataSet = {
    if (targetType == dataType) {
      return this
    }
    AutoSelect.autoSelect(dataType, targetType) match {
      case Left(msg)     => throw new ConversionNotApplicableException(msg)
      case Right(select) => this.select(select)
    }
  }

  /**
   * Returns a dataset, which will be cached.
   * Note: caching is done lazy.
   */
  def cached: DataSet = {
    val itemId = ItemId.generate()
    payloadSource match {
      case c: PayloadSource.Cached => this
      case _ =>
        val updatedSource = Source(
          DefinitionSource.Derived(source.definition),
          PayloadSource.Cached(source.payload, Vector(itemId))
        )
        copy(
          core = core.copy(source = updatedSource, itemId = itemId)
        )
    }
  }

  override protected def withCore(core: MantikItemCore[DataSetDefinition]): DataSet = {
    copy(core = core)
  }
}

object DataSet {

  def literal(bundle: Bundle): DataSet = {
    natural(
      Source.constructed(PayloadSource.BundleLiteral(bundle)), bundle.model
    )
  }

  def apply(source: Source, mantikHeader: MantikHeader[DataSetDefinition], bridge: Bridge): DataSet = {
    DataSet(MantikItemCore(source, mantikHeader, bridge = bridge))
  }

  implicit val encoder: Encoder[DataSet] = MantikItem.encoder.contramap(x => x: MantikItem)
  implicit val decoder: Decoder[DataSet] = MantikItem.decoder.emap {
    case ds: DataSet   => Right(ds)
    case somethingElse => Left(s"Expected DataSet, got ${somethingElse.getClass.getSimpleName}")
  }

  /** Creates a natural data source, with serialized data coming direct from a flow. */
  private[planner] def natural(source: Source, dataType: DataType): DataSet = {
    val bridge = Bridge.naturalBridge
    DataSet(source, MantikHeader.pure(
      elements.DataSetDefinition(
        bridge = bridge.mantikId,
        `type` = dataType
      )
    ), bridge)
  }
}
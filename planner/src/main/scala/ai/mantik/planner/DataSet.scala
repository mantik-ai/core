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
package ai.mantik.planner

import ai.mantik.bridge.scalafn.{RowMapper, ScalaFnDefinition, ScalaFnPayload, ScalaFnType, UserDefinedFunction}
import ai.mantik.ds.Errors.{DataTypeMismatchException, FeatureNotSupported}
import ai.mantik.ds.{DataType, TabularData}
import ai.mantik.ds.element.{Bundle, SingleElementBundle, TabularRow}
import ai.mantik.ds.functional.FunctionConverter
import ai.mantik.ds.sql.{
  AnonymousInput,
  AutoSelect,
  AutoUnion,
  Join,
  JoinType,
  Query,
  Select,
  SingleQuery,
  Split,
  SqlContext
}
import ai.mantik.elements
import ai.mantik.elements.{DataSetDefinition, ItemId, MantikHeader, NamedMantikId}
import ai.mantik.planner.repository.Bridge
import io.circe.{Decoder, Encoder}

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
    val parsedSelect = Select.parse(forceDataTypeTabular(), statement) match {
      case Left(error) => throw new IllegalArgumentException(s"Could not parse select ${error}")
      case Right(ok)   => ok
    }
    select(parsedSelect)
  }

  /** Derives a data set, as the result of applying a select to this dataset. */
  def select(select: Select): DataSet = {
    if (select.inputTabularType != dataType) {
      throw new IllegalArgumentException("Select statement is for a different data type and not applicable")
    }

    val payloadSource = PayloadSource.OperationResult(
      Operation.SqlQueryOperation(
        SingleQuery(select),
        Vector(this)
      )
    )
    DataSet.natural(Source.constructed(payloadSource), select.resultingTabularType)
  }

  @throws[FeatureNotSupported]("If data type is not tabular")
  private[planner] def forceDataTypeTabular(): TabularData = {
    dataType match {
      case td: TabularData => td
      case _               => throw new FeatureNotSupported(s"Operation only supported for tabular data")
    }
  }

  /** Performs inner join with other dataset */
  def join(other: DataSet, columns: Seq[String]): DataSet = {
    join(other, columns, JoinType.Inner)
  }

  /** Performs join with other dataset. */
  def join(other: DataSet, columns: Seq[String], joinType: JoinType): DataSet = {
    val joinOp =
      Join.anonymousFromUsing(forceDataTypeTabular(), other.forceDataTypeTabular(), columns, joinType) match {
        case Left(error) => throw new IllegalArgumentException(s"Illegal join ${error}")
        case Right(ok)   => ok
      }
    join(other, joinOp)
  }

  /** Performs a custom join with other dataset */
  private def join(other: DataSet, join: Join): DataSet = {
    DataSet.query(join, Vector(this, other))
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
    DataSet.query(autoUnion, Vector(this, other))
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

  /**
    * Split the DataSet into multiple fractions.
    * @param fractions the relative size of each fraction
    * @param shuffleSeed if given, shuffle the dataset before extracting fractions using this Random seed
    * @param cached if true, cache sub results to avoid recalculation
    * @return fractions.length + 1 DataSets
    */
  def split(fractions: Seq[Double], shuffleSeed: Option[Long] = None, cached: Boolean = true): Seq[DataSet] = {
    val tabularType = forceDataTypeTabular()
    val underlying = AnonymousInput(tabularType)
    val split = Split(
      underlying,
      fractions.toVector,
      shuffleSeed
    )

    val resultCount = fractions.size + 1

    val operationResult = PayloadSource.OperationResult(
      Operation.SqlQueryOperation(
        split,
        Vector(this)
      )
    )

    val resultItemIds = Vector.fill(resultCount)(ItemId.generate())

    val maybeCachedOperationResult = if (cached) {
      PayloadSource.Cached(operationResult, resultItemIds)
    } else {
      operationResult
    }

    val dataSets = for (resultId <- 0 until resultCount) yield {
      DataSet
        .natural(Source.constructed(PayloadSource.Projection(maybeCachedOperationResult, resultId)), tabularType)
        .withItemId(
          resultItemIds(resultId)
        )
    }
    dataSets
  }

  /**
    * Maps rows into a new structure using the ScalaFn-Bridge.
    *
    * Note: this method is very low level, type errors are not detected.
    */
  @throws[FeatureNotSupported]("If the input data is not tabular")
  def mapRows(
      destinationType: TabularData
  )(rowMapper: RowMapper): DataSet = {
    val inputTabular = forceDataTypeTabular()
    val payload = ScalaFnPayload.serialize(rowMapper)
    val definition = ScalaFnDefinition(
      fnType = ScalaFnType.RowMapperType,
      input = Vector(inputTabular),
      output = Vector(destinationType),
      payload = payload
    )
    val operation = Operation.ScalaFnOperation(definition, Vector(this))

    val payloadSource = PayloadSource.OperationResult(operation)
    DataSet.natural(Source.constructed(payloadSource), destinationType)
  }

  /**
    * Map rows into a new structure using the ScalaFn-Bridge.
    *
    * The structure of the mapping function is automatically detected.
    * The resulting columns will be named like _1, _2, etc.
    */
  @throws[DataTypeMismatchException]("If the data types can't be applied")
  @throws[FeatureNotSupported]("If the input data is not tabular")
  def mapRowsFn[I, O](
      udf: UserDefinedFunction[I, O]
  ): DataSet = {
    val inputTabular = forceDataTypeTabular()
    val resultingType = udf.functionConverter.destinationTypeAsTable()
    val inputDecoder = udf.functionConverter.buildDecoderForTables(inputTabular)
    val outputEncoder = udf.functionConverter.buildEncoderForTables()
    val fullFunction = inputDecoder.andThen(udf.fn).andThen(outputEncoder)
    mapRows(resultingType)(RowMapper(fullFunction))
  }

  override protected def withCore(core: MantikItemCore[DataSetDefinition]): DataSet = {
    copy(core = core)
  }
}

object DataSet {

  def literal(bundle: Bundle): DataSet = {
    natural(
      Source.constructed(PayloadSource.BundleLiteral(bundle)),
      bundle.model
    )
  }

  def apply(source: Source, mantikHeader: MantikHeader[DataSetDefinition], bridge: Bridge): DataSet = {
    DataSet(MantikItemCore(source, mantikHeader, bridge = bridge))
  }

  /**
    * Execute an SQL Query on Datasets.
    * @param query SQL Query
    * @param arguments arguments to be used within SQL query. They are accessible as $0, $1, ...
    */
  def query(
      statement: String,
      arguments: DataSet*
  ): DataSet = {
    implicit val sqlContext = SqlContext(arguments.map { ds =>
      ds.forceDataTypeTabular()
    }.toVector)
    val parsedQuery = Query.parse(statement) match {
      case Left(error) => throw new IllegalArgumentException(s"Could not parse query ${error}")
      case Right(ok)   => ok
    }
    query(parsedQuery, arguments.toVector)
  }

  private def query(
      query: Query,
      arguments: Vector[DataSet]
  ): DataSet = {
    val inputs = query.figureOutInputPorts.getOrElse {
      throw new IllegalArgumentException(s"Illegal input ports")
    }
    if (inputs.length != arguments.length) {
      throw new IllegalArgumentException(s"Wrong count of inputs, expected ${inputs.length}, got ${arguments.length}")
    }
    inputs.zip(arguments).foreach { case (input, arg) =>
      if (input != arg.forceDataTypeTabular()) {
        throw new IllegalArgumentException(s"Bad input type, expected ${input}, got ${arg.forceDataTypeTabular()}")
      }
    }
    val payloadSource = PayloadSource.OperationResult(
      Operation.SqlQueryOperation(
        SingleQuery(query),
        arguments
      )
    )
    DataSet.natural(Source.constructed(payloadSource), query.resultingTabularType)
  }

  implicit val encoder: Encoder[DataSet] = MantikItem.encoder.contramap(x => x: MantikItem)
  implicit val decoder: Decoder[DataSet] = MantikItem.decoder.emap {
    case ds: DataSet   => Right(ds)
    case somethingElse => Left(s"Expected DataSet, got ${somethingElse.getClass.getSimpleName}")
  }

  /** Creates a natural data source, with serialized data coming direct from a flow. */
  private[planner] def natural(source: Source, dataType: DataType): DataSet = {
    val bridge = Bridge.naturalBridge
    DataSet(
      source,
      MantikHeader.pure(
        elements.DataSetDefinition(
          bridge = bridge.mantikId,
          `type` = dataType
        )
      ),
      bridge
    )
  }
}

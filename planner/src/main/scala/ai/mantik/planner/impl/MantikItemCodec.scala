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
package ai.mantik.planner.impl

import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import ai.mantik.ds.sql.{MultiQuery, Query, Select, SingleQuery, SqlContext}
import ai.mantik.ds.{DataType, TabularData}
import ai.mantik.elements._
import ai.mantik.planner.pipelines.{ResolvedPipeline, ResolvedPipelineStep}
import ai.mantik.planner.repository.Bridge
import ai.mantik.planner._
import ai.mantik.planner.pipelines.ResolvedPipelineStep.{AlgorithmStep, SelectStep}
import io.circe.generic.semiauto
import io.circe.{Decoder, Encoder, ObjectEncoder}

import scala.reflect.{ClassTag, classTag}

/**
  * Serialization Codec for Mantik Items
  * Note: the format is bound to the MantikItem class types. This should be simplified in future.
  */
private[mantik] object MantikItemCodec extends DiscriminatorDependentCodec[MantikItem]("type") {
  implicit private def selfEncoder: Encoder[MantikItem] = this
  implicit private def selfDecoder: Decoder[MantikItem] = this

  implicit object definitionSourceCodec extends DiscriminatorDependentCodec[DefinitionSource]("type") {
    override val subTypes = Seq(
      makeSubType[DefinitionSource.Loaded]("loaded"),
      makeSubType[DefinitionSource.Constructed]("constructed"),
      makeSubType[DefinitionSource.Derived]("derived"),
      makeSubType[DefinitionSource.Tagged]("tagged")
    )
  }

  implicit object operationCodec extends DiscriminatorDependentCodec[Operation]("type") {
    implicit val dataSetEncoder = pureDatasetEncoder
    implicit val dataSetDecoder = pureDatasetDecoder

    private implicit val applicableMantikItemEncoder: Encoder[ApplicableMantikItem] =
      MantikItemCodec.this.contramap(x => x: MantikItem)
    private implicit val applicableMantikItemDecoder: Decoder[ApplicableMantikItem] = MantikItemCodec.this.emap {
      case a: ApplicableMantikItem => Right(a)
      case somethingElse           => Left(s"Expected ApplicableMantikItem, got ${somethingElse.getClass.getSimpleName}")
    }

    private implicit val trainableItemEncoder: Encoder[TrainableAlgorithm] =
      MantikItemCodec.this.contramap(x => x: MantikItem)
    private implicit val trainableItemDecoder: Decoder[TrainableAlgorithm] = MantikItemCodec.this.emap {
      case t: TrainableAlgorithm => Right(t)
      case somethingElse         => Left(s"Expected TrainableMantikItem, got ${somethingElse.getClass.getSimpleName}")
    }

    override val subTypes = Seq(
      makeSubType[Operation.Application]("application"),
      makeSubType[Operation.Training]("training"),
      makeSubType[Operation.SqlQueryOperation]("sqlQuery")
    )
  }

  implicit object payloadSourceCodec extends DiscriminatorDependentCodec[PayloadSource]("type") {
    override val subTypes = Seq(
      makeSubType[PayloadSource.Loaded]("loaded"),
      makeSubType[PayloadSource.BundleLiteral]("bundle"),
      makeSubType[PayloadSource.Cached]("cached"),
      makeSubType[PayloadSource.OperationResult]("operation_result"),
      makeSubType[PayloadSource.Empty.type]("empty"),
      makeSubType[PayloadSource.Projection]("projection")
    )
  }

  implicit val sourceEncoder: Encoder[Source] = semiauto.deriveEncoder[Source]
  implicit val sourceDecoder: Decoder[Source] = semiauto.deriveDecoder[Source]

  implicit val bridgeEncoder: Encoder[Bridge] = this.contramap(x => x: MantikItem)
  implicit val bridgeDecoder: Decoder[Bridge] = this.emap {
    case b: Bridge     => Right(b)
    case somethingElse => Left(s"Expected Bridge, got ${somethingElse.getClass.getSimpleName}")
  }

  implicit def mantikItemCoreEncoder[T <: MantikDefinition]: Encoder[MantikItemCore[T]] =
    semiauto.deriveEncoder[MantikItemCore[T]]
  implicit def mantikItemCoreDecoder[T <: MantikDefinition: ClassTag]: Decoder[MantikItemCore[T]] = {
    implicit val headerDecoder = MantikHeader.decoder[T]
    semiauto.deriveDecoder[MantikItemCore[T]]
  }

  private val pureDatasetEncoder: Encoder.AsObject[DataSet] = semiauto.deriveEncoder[DataSet]
  private val pureDatasetDecoder: Decoder[DataSet] = {
    implicit val itemDecoder = mantikItemCoreDecoder[DataSetDefinition]
    semiauto.deriveDecoder[DataSet]
  }

  case class EncodedQuery(
      inputs: Vector[TabularData],
      statement: String
  )

  private implicit val queryEncoder: Encoder[MultiQuery] =
    semiauto.deriveEncoder[EncodedQuery].contramap { s =>
      val inputPorts = s.figureOutInputPorts match {
        case Left(error) => throw new IllegalArgumentException(s"Invalid query input ports ${error}")
        case Right(ok) =>
          ok
      }
      EncodedQuery(inputPorts, s.toStatement)
    }

  private implicit val selectEncoder: Encoder[Select] = queryEncoder.contramap { s => SingleQuery(s): MultiQuery }

  private implicit val queryDecoder: Decoder[MultiQuery] =
    semiauto.deriveDecoder[EncodedQuery].emap { encoded =>
      implicit val sqlContext = SqlContext(encoded.inputs)
      MultiQuery.parse(encoded.statement)
    }

  private implicit val selectDecoder: Decoder[Select] = queryDecoder.emap {
    case SingleQuery(s: Select) => Right(s)
    case other                  => Left(s"Expected Select, got ${other.getClass.getSimpleName}")
  }

  private val pureAlgorithmEncoder: Encoder.AsObject[Algorithm] = semiauto.deriveEncoder[Algorithm]
  private val pureAlgorithmDecoder: Decoder[Algorithm] = {
    implicit val itemDecoder = mantikItemCoreDecoder[AlgorithmDefinition]
    semiauto.deriveDecoder[Algorithm]
  }

  private val pureTrainableEncoder: Encoder.AsObject[TrainableAlgorithm] =
    semiauto.deriveEncoder[TrainableAlgorithm]
  private val pureTrainableDecoder: Decoder[TrainableAlgorithm] = {
    implicit val itemDecoder = mantikItemCoreDecoder[TrainableAlgorithmDefinition]
    semiauto.deriveDecoder[TrainableAlgorithm]
  }

  private val pureBridgeEncoder: Encoder.AsObject[Bridge] = semiauto.deriveEncoder[Bridge]
  private val pureBridgeDecoder: Decoder[Bridge] = {
    implicit val itemDecoder = mantikItemCoreDecoder[BridgeDefinition]
    semiauto.deriveDecoder[Bridge]
  }

  implicit val resolvedPipelineStep: Encoder.AsObject[ResolvedPipelineStep] with Decoder[ResolvedPipelineStep] =
    new DiscriminatorDependentCodec[ResolvedPipelineStep]() {
      implicit val algoEncoder = pureAlgorithmEncoder
      implicit val algoDecoder = pureAlgorithmDecoder
      implicit val algorithmStepEncoder = semiauto.deriveEncoder[AlgorithmStep]
      implicit val algorithmStepDecoder = semiauto.deriveDecoder[AlgorithmStep]
      override val subTypes = Seq(
        makeGivenSubType[AlgorithmStep]("algorithm"),
        makeSubType[SelectStep]("select")
      )
    }

  implicit private val resolvedPipelineDefinitionEncoder: Encoder[ResolvedPipeline] = {
    semiauto.deriveEncoder[ResolvedPipeline]
  }

  implicit private val resolvedPipelineDefinitionDecoder: Decoder[ResolvedPipeline] = {
    semiauto.deriveDecoder[ResolvedPipeline]
  }

  private val purePipelineEncoder: Encoder.AsObject[Pipeline] = semiauto.deriveEncoder[Pipeline]
  private val purePipelineDecoder: Decoder[Pipeline] = {
    implicit val itemDecoder = mantikItemCoreDecoder[PipelineDefinition]
    semiauto.deriveDecoder[Pipeline]
  }

  override val subTypes = Seq(
    makeGivenSubType[DataSet]("dataset")(classTag[DataSet], pureDatasetEncoder, pureDatasetDecoder),
    makeGivenSubType[Algorithm]("algorithm")(classTag[Algorithm], pureAlgorithmEncoder, pureAlgorithmDecoder),
    makeGivenSubType[TrainableAlgorithm]("trainable")(
      classTag[TrainableAlgorithm],
      pureTrainableEncoder,
      pureTrainableDecoder
    ),
    makeGivenSubType[Bridge]("bridge")(classTag[Bridge], pureBridgeEncoder, pureBridgeDecoder),
    makeGivenSubType[Pipeline]("pipeline")(classTag[Pipeline], purePipelineEncoder, purePipelineDecoder)
  )

}

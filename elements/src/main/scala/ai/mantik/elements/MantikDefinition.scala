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
package ai.mantik.elements

import ai.mantik.ds.DataType
import ai.mantik.ds.functional.FunctionType
import ai.mantik.ds.helper.circe.{CirceJson, DiscriminatorDependentCodec}
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, Encoder, ObjectEncoder}

import scala.util.matching.Regex

/** A Basic Mantik Definition (algorithms, datasets, etc...) */
sealed trait MantikDefinition {
  def kind: String

  /** Returns referenced items. */
  def referencedItems: Seq[MantikId] = Nil
}

object MantikDefinition extends DiscriminatorDependentCodec[MantikDefinition] {
  override val subTypes = Seq(
    // Not using constants, they are not yet initialized.
    makeSubType[AlgorithmDefinition]("algorithm", isDefault = true),
    makeGivenSubType[BridgeDefinition]("bridge"),
    makeSubType[DataSetDefinition]("dataset"),
    makeSubType[TrainableAlgorithmDefinition]("trainable"),
    makeSubType[PipelineDefinition]("pipeline"),
    makeSubType[CombinerDefinition]("combiner")
  )

  val BridgeKind = "bridge"
  val AlgorithmKind = "algorithm"
  val DataSetKind = "dataset"
  val TrainableAlgorithmKind = "trainable"
  val PipelineKind = "pipeline"
  val CombinerKind = "combiner"
}

/** A MantikDefinition which doesn't need a bridge. */
sealed trait MantikDefinitionWithoutBridge extends MantikDefinition

/**
  * A Bridge definition.
  *
  * @param protocol 0 ... Just pipe out DataSet, 1 ... Regular Format.
  * @param payloadContentType if set, the bridge expects a payload content type.
  */
case class BridgeDefinition(
    dockerImage: String,
    suitable: Seq[String],
    protocol: Int = 1,
    payloadContentType: Option[String] = Some("application/zip")
) extends MantikDefinitionWithoutBridge {
  override def kind: String = MantikDefinition.BridgeKind
}

object BridgeDefinition {
  // BridgeDefinition has DefaultValues, so it gets a special treating
  import io.circe.generic.extras.semiauto
  private implicit val config = Configuration.default.withDefaults
  implicit val encoder: Encoder.AsObject[BridgeDefinition] = semiauto.deriveConfiguredEncoder[BridgeDefinition]
  implicit val decoder: Decoder[BridgeDefinition] = semiauto.deriveConfiguredDecoder[BridgeDefinition]
}

/** A MantikDefinition which needs a Bridge. */
sealed trait MantikDefinitionWithBridge extends MantikDefinition {

  /** Returns the name of the bridge. */
  def bridge: MantikId

  override def referencedItems: Seq[MantikId] = Seq(bridge)
}

/** An Algorithm Definition inside a MantikHeader. */
case class AlgorithmDefinition(
    // specific
    bridge: MantikId,
    `type`: FunctionType
) extends MantikDefinitionWithBridge {
  def kind = MantikDefinition.AlgorithmKind
}

/** A DataSet definition inside a MantikHeader */
case class DataSetDefinition(
    bridge: MantikId,
    `type`: DataType
) extends MantikDefinitionWithBridge {
  def kind = MantikDefinition.DataSetKind
}

case class TrainableAlgorithmDefinition(
    bridge: MantikId,
    trainedBridge: Option[MantikId] = None, // if not given, the bridge will be used.
    `type`: FunctionType,
    trainingType: DataType,
    statType: DataType
) extends MantikDefinitionWithBridge {

  override def referencedItems: Seq[MantikId] = super.referencedItems ++ trainedBridge

  def kind = MantikDefinition.TrainableAlgorithmKind
}

/**
  * A Pipeline. A special item which refers to other algorithm items which
  * executed after each other.
  */
case class PipelineDefinition(
    // Note: the type is optional,
    `type`: Option[OptionalFunctionType] = None,
    steps: List[PipelineStep]
) extends MantikDefinitionWithoutBridge {

  override def kind: String = MantikDefinition.PipelineKind

  def inputType: Option[DataType] = `type`.flatMap(_.input)

  def outputType: Option[DataType] = `type`.flatMap(_.output)

  override def referencedItems: Seq[MantikId] = {
    steps.collect { case as: PipelineStep.AlgorithmStep =>
      as.algorithm
    }
  }
}

/** A Function type where input/output are optional. */
case class OptionalFunctionType(
    input: Option[DataType] = None,
    output: Option[DataType] = None
)

object OptionalFunctionType {
  implicit val codec: Encoder[OptionalFunctionType] with Decoder[OptionalFunctionType] =
    CirceJson.makeSimpleCodec[OptionalFunctionType]
}

/** Combiner Definition (e.g. for SQL Operations) */
case class CombinerDefinition(
    bridge: MantikId,
    input: Seq[DataType],
    output: Seq[DataType]
) extends MantikDefinitionWithBridge {
  override def kind: String = MantikDefinition.CombinerKind
}

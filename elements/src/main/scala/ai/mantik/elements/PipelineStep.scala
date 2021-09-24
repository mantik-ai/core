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

import ai.mantik.ds.helper.circe.{CirceJson, TrialDependentCodec}
import io.circe.{Decoder, Json, Encoder}

/** A Single step within a [[PipelineDefinition]]. */
sealed trait PipelineStep {

  /** Optional description. */
  def description: Option[String]
}

object PipelineStep {

  /** Execute a algorithm. */
  case class AlgorithmStep(
      algorithm: MantikId,
      description: Option[String] = None,
      metaVariables: Option[List[MetaVariableSetting]] = None
  ) extends PipelineStep

  /** A Setting for a meta variable, used in Pipelines. */
  case class MetaVariableSetting(
      name: String,
      value: Json
  )

  /** Executes a SELECT Statement. */
  case class SelectStep(
      select: String,
      description: Option[String] = None
  ) extends PipelineStep

  implicit val metaVariableCodec: Encoder.AsObject[MetaVariableSetting] with Decoder[MetaVariableSetting] =
    CirceJson.makeSimpleCodec[MetaVariableSetting]

  implicit val codec = new TrialDependentCodec[PipelineStep] {
    override val subTypes: Seq[SubType[_ <: PipelineStep]] = Seq(
      makeSubType[AlgorithmStep](),
      makeSubType[SelectStep]()
    )
  }
}

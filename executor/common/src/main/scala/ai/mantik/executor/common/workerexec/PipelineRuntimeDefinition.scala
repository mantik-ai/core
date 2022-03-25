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
package ai.mantik.executor.common.workerexec

import io.circe.Json
import io.circe.generic.JsonCodec

/**
  * Runtime definition of pipelines.
  * Must be compatible with the Golang Pipeline controller definition.
  *
  * Note: input and output type must be in DS-Encoding.
  */
@JsonCodec
case class PipelineRuntimeDefinition(
    name: String,
    steps: Seq[PipelineRuntimeDefinition.Step],
    inputType: Json,
    outputType: Json
)

object PipelineRuntimeDefinition {
  @JsonCodec
  case class Step(
      url: String
  )
}
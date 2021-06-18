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
package ai.mantik.ui.model

import ai.mantik.ds.helper.circe.EnumDiscriminatorCodec
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec

import java.time.Instant

/** An Operation, part of a job */
@JsonCodec
case class Operation(
    id: OperationId,
    state: OperationState,
    error: Option[String] = None,
    start: Option[Instant] = None,
    end: Option[Instant] = None,
    definition: OperationDefinition
)

object Operation {}

/** The state of an operation */
sealed trait OperationState

object OperationState {
  case object Pending extends OperationState
  case object Running extends OperationState
  case object Failed extends OperationState
  case object Done extends OperationState

  implicit val codec: Encoder[OperationState] with Decoder[OperationState] =
    new EnumDiscriminatorCodec[OperationState](
      Seq(
        "pending" -> Pending,
        "running" -> Running,
        "failed" -> Failed,
        "done" -> Done
      )
    )
}

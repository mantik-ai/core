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
import io.circe.generic.{JsonCodec, semiauto}

import java.time.Instant

/** Full information about a job */
@JsonCodec
case class Job(
    header: JobHeader,
    operations: Seq[Operation]
)

/**
  * Simple parts of a job
  * @param id id of the Job
  * @param name optional name
  * @param error error message, if the job failed
  * @param state state of the job
  * @param registration job registration time
  * @param start running start time of the job
  * @param end end/failure time of the job
  */
@JsonCodec
case class JobHeader(
    id: String,
    name: Option[String] = None,
    error: Option[String] = None,
    state: JobState,
    registration: Instant,
    start: Option[Instant] = None,
    end: Option[Instant] = None
)

sealed trait JobState

object JobState {
  case object Pending extends JobState
  case object Running extends JobState
  case object Failed extends JobState
  case object Done extends JobState

  implicit val codec: Encoder[JobState] with Decoder[JobState] = new EnumDiscriminatorCodec[JobState](
    Seq(
      "pending" -> Pending,
      "running" -> Running,
      "failed" -> Failed,
      "done" -> Done
    )
  )
}

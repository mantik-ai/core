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
package ai.mantik.executor.model

import ai.mantik.executor.model.docker.Container
import io.circe.generic.JsonCodec

/**
  * Request current workers.
  */
case class ListWorkerRequest(
    nameFilter: Option[String] = None,
    idFilter: Option[String] = None
)

/** Response for [[ListWorkerRequest]] */
case class ListWorkerResponse(
    workers: Seq[ListWorkerResponseElement]
)

sealed trait WorkerState {
  def isTerminal: Boolean
}

object WorkerState {
  case object Pending extends WorkerState {
    override def isTerminal: Boolean = false
  }
  case object Running extends WorkerState {
    override def isTerminal: Boolean = false
  }
  case class Failed(status: Int, error: Option[String] = None) extends WorkerState {
    override def isTerminal: Boolean = true
  }
  case object Succeeded extends WorkerState {
    override def isTerminal: Boolean = true
  }
}

sealed trait WorkerType

object WorkerType {
  case object MnpWorker extends WorkerType
  case object MnpPipeline extends WorkerType
}

case class ListWorkerResponseElement(
    nodeName: String,
    id: String,
    container: Option[Container],
    state: WorkerState,
    `type`: WorkerType,
    externalUrl: Option[String]
)

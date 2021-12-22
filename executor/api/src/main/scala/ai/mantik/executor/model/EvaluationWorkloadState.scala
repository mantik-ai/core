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

/** Status of a workload or a part of it in one Enumeration. */
sealed trait WorkloadStatus

object WorkloadStatus {
  case class Preparing(what: Option[String] = None) extends WorkloadStatus {
    override def toString: String = {
      what match {
        case Some(given) => s"Preparing (${given})"
        case None        => s"Preparing"
      }
    }
  }
  case object Running extends WorkloadStatus
  case object Done extends WorkloadStatus
  case class Failed(error: String) extends WorkloadStatus
}

/** State of a container */
case class ContainerState(
    status: WorkloadStatus
)

/** State of a session */
case class SessionState(
    status: WorkloadStatus
)

/** State of a link. */
case class LinkState(
    status: WorkloadStatus,
    transferred: Option[Long] = None
) {

  /** Increment transferred data, initializing if necessary */
  def incrementTransferred(by: Long): LinkState = {
    copy(
      transferred = Some(transferred.getOrElse(0L) + by)
    )
  }
}

/** Contains the state of an evalution workload.
  * @param status state of the workload.
  * @param canceled true if the workload was canceled
  * @param containers container states, if available.
  * @param sessions session states, if available.
  * @param links link states, if available
  */
case class EvaluationWorkloadState(
    status: WorkloadStatus,
    canceled: Boolean,
    containers: Option[Vector[ContainerState]] = None,
    sessions: Option[Vector[SessionState]] = None,
    links: Option[Vector[LinkState]] = None
)

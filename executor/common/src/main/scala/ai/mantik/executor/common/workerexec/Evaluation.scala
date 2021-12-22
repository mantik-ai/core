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

import ai.mantik.executor.model.{EvaluationWorkload, EvaluationWorkloadState, WorkloadStatus}
import ai.mantik.mnp.protocol.mnp.AboutResponse
import ai.mantik.mnp.{MnpAddressUrl, MnpClient, MnpSession, MnpSessionUrl}

/** Holds the state of a running evaluation. */
case class Evaluation(
    workload: EvaluationWorkload,
    state: EvaluationWorkloadState,
    containers: Option[RunningContainers] = None,
    sessions: Option[RunningSessions] = None
) {
  def stateUpdate(f: EvaluationWorkloadState => EvaluationWorkloadState): Evaluation = {
    copy(
      state = f(state)
    )
  }

  /** Update the main workload state */
  def withWorkloadState(workloadState: WorkloadStatus): Evaluation = {
    copy(
      state = state.copy(
        status = workloadState
      )
    )
  }
}

case class ReservedContainer(
    name: String,
    image: String,
    mnpAddress: MnpAddressUrl,
    mnpClient: MnpClient,
    aboutResponse: AboutResponse
)

/** Running containers. */
case class RunningContainers(
    containers: Vector[ReservedContainer]
)

/** A Running session. */
case class RunningSession(
    mnpSession: MnpSession,
    mnpSessionUrl: MnpSessionUrl
)

/** Running sessions. */
case class RunningSessions(
    sessions: Vector[RunningSession]
)

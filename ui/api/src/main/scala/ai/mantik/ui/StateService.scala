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
package ai.mantik.ui

import ai.mantik.ui.model.{
  JobHeader,
  JobResponse,
  JobsResponse,
  OperationId,
  RunGraphResponse,
  SettingsResponse,
  VersionResponse
}

import scala.concurrent.Future

/** Gives out current state to the UI Server */
trait StateService {

  /** Returns the current version */
  def version: VersionResponse

  /** Return Mantik settings */
  def settings: SettingsResponse

  /** Returns the list of present jobs
    * @param pollVersion if given with current version, poll for the next version
    */
  def jobs(pollVersion: Option[Long]): Future[JobsResponse]

  /** Returns information about a job.
    * @param pollVersion if given with current version, poll for the next version
    */
  def job(id: String, pollVersion: Option[Long]): Future[JobResponse]

  /** Returns information about a run graph
    * @param pollVersion if given with current version, poll for the next version
    */
  def runGraph(jobId: String, operationId: OperationId, pollVersion: Option[Long]): Future[RunGraphResponse]

}

object StateService {
  class StateServiceException(msg: String = null) extends RuntimeException(msg)
  class EntryNotFoundException(msg: String = null) extends StateServiceException(msg)
}

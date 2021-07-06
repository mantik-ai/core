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
package ai.mantik.executor

import ai.mantik.componently.Component
import ai.mantik.executor.model._

import scala.concurrent.Future

/** Defines the interface for the Executor. */
trait Executor extends Component {

  /**
    * Publish a external service to the cluster.
    * Note: this only for simplifying local deployments.
    */
  def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse]

  /** Returns the name and version string of the server (displayed on about page). */
  def nameAndVersion: Future[String]

  /** Returns the gRpc proxy which enables the Engine to communicate with MNP Nodes. */
  def grpcProxy(): Future[GrpcProxy]

  /** Start a new Worker. */
  def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse]

  /** List workers. */
  def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse]

  /** Stop worker(s). */
  def stopWorker(stopWorkerRequest: StopWorkerRequest): Future[StopWorkerResponse]
}

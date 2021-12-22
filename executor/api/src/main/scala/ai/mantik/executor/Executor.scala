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

import ai.mantik.executor.model.{
  EvaluationDeploymentRequest,
  EvaluationDeploymentResponse,
  EvaluationWorkloadRequest,
  EvaluationWorkloadResponse,
  EvaluationWorkloadState,
  ListResponse
}
import akka.actor.Cancellable
import akka.stream.scaladsl.Source

import scala.concurrent.Future

/** Defines the interface for the Executor. */
trait Executor {

  /** Returns the name and version string of the server (displayed on about page). */
  def nameAndVersion: Future[String]

  /** Start a new evaluation. */
  def startEvaluation(evaluationWorkloadRequest: EvaluationWorkloadRequest): Future[EvaluationWorkloadResponse]

  /** Get the state of evaluation */
  def getEvaluation(id: String): Future[EvaluationWorkloadState]

  /** Track evaluation. */
  def trackEvaluation(id: String): Source[EvaluationWorkloadState, Cancellable]

  /** Cancel the evaluation of something. */
  def cancelEvaluation(id: String): Future[Unit]

  /** Start a deployment. May not be supported. */
  def startDeployment(deploymentRequest: EvaluationDeploymentRequest): Future[EvaluationDeploymentResponse] =
    Future.failed(
      new Errors.NotSupportedException(s"Deployments not supported by Executor")
    )

  /** Remove a Deployment. May not be supported. */
  def removeDeployment(id: String): Future[Unit] = Future.failed(
    new Errors.NotSupportedException(s"Deployments not supported by Executor")
  )

  /** List running elements. */
  def list(): Future[ListResponse]
}

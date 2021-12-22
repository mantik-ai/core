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

import ai.mantik.bridge.protocol.bridge.MantikInitConfiguration
import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.common.workerexec.model.{ListWorkerRequest, WorkerState, WorkerType}
import ai.mantik.executor.model._
import ai.mantik.executor.{Errors, Executor, PayloadProvider}
import ai.mantik.mnp.protocol.mnp.{ConfigureInputPort, ConfigureOutputPort, InitRequest}
import akka.actor.Cancellable
import akka.stream.scaladsl.Source
import cats.implicits._
import com.google.protobuf.any.Any

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Adapts the evaluation model to the older worker model. */
class WorkerBasedExecutor(
    backend: WorkerExecutorBackend,
    metrics: WorkerMetrics,
    payloadProvider: PayloadProvider
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with Executor {

  /** Contains current state. */
  private val stateTracker = new StateTracker()

  private val workerManager = new WorkerManager(stateTracker, backend, metrics)
  private val sessionManager = new SessionManager(stateTracker, payloadProvider)
  private val deploymentManager = new DeploymentManager(backend, metrics, sessionManager, payloadProvider)

  override def nameAndVersion: Future[String] = {
    backend.nameAndVersion
  }

  override def startEvaluation(
      evaluationWorkloadRequest: EvaluationWorkloadRequest
  ): Future[EvaluationWorkloadResponse] = {
    val violations = evaluationWorkloadRequest.violations
    if (violations.nonEmpty) {
      return Future.failed(
        new Errors.BadRequestException(s"Invalid evaluation request: ${violations}")
      )
    }
    val initial = Evaluation(
      evaluationWorkloadRequest.workload,
      state = EvaluationWorkloadState(
        status = WorkloadStatus.Preparing(),
        canceled = false,
        containers = Some(
          Vector.fill(evaluationWorkloadRequest.workload.containers.size)(
            ContainerState(status = WorkloadStatus.Preparing())
          )
        ),
        sessions = Some(
          Vector.fill(evaluationWorkloadRequest.workload.sessions.size)(
            SessionState(status = WorkloadStatus.Preparing())
          )
        ),
        links = Some(
          Vector.fill(evaluationWorkloadRequest.workload.links.size)(
            LinkState(status = WorkloadStatus.Preparing())
          )
        )
      )
    )

    val id = evaluationWorkloadRequest.workload.id

    stateTracker.add(initial) match {
      case Some(existing) =>
        Future.failed(new Errors.ConflictException(s"Id ${id} already exists (in state ${existing.state.status})"))
      case None =>
        runEvaluation(initial.workload)
        Future.successful(EvaluationWorkloadResponse())
    }
  }

  private def runEvaluation(workload: EvaluationWorkload): Unit = {
    logger.info(s"Starting Workload ${workload.id}")

    withRunningContainers(workload) { runningContainers =>
      runEvaluationWithContainers(workload, runningContainers)
    }.andThen {
      case Success(_) =>
        stateTracker.updateWorkloadState(workload.id, WorkloadStatus.Done)
        stateTracker.finishEvaluation(workload.id)
      case Failure(exception) =>
        stateTracker.updateWorkloadState(
          workload.id,
          WorkloadStatus.Failed(s"Evaluation failed: ${Option(exception).getOrElse("unknown")}")
        )
        stateTracker.finishEvaluation(workload.id)
    }
  }

  /** Run the block in f with running containers and stop them afterwards. */
  private def withRunningContainers[T](workload: EvaluationWorkload)(f: RunningContainers => Future[T]): Future[T] = {
    stateTracker.updateWorkloadState(workload.id, WorkloadStatus.Preparing(Some("Starting Containers")))
    workerManager.prepareContainers(workload).flatMap { runningContainers =>
      FutureHelper.andThenAsync(
        f(runningContainers)
      ) { _ =>
        workerManager.shutdownContainers(workload.id, runningContainers)
      }
    }
  }

  /** Run the evaluation itself with the containers present. */
  private def runEvaluationWithContainers(
      workload: EvaluationWorkload,
      runningContainers: RunningContainers
  ): Future[Unit] = {
    stateTracker.updateState(workload.id)(
      _.copy(
        containers = Some(runningContainers)
      ).withWorkloadState(WorkloadStatus.Preparing(Some("Starting Sessions")))
    )

    def checkCanceled(): Future[Unit] = {
      if (stateTracker.get(workload.id).exists(_.state.canceled)) {
        Future.failed(new Errors.InternalException(s"Workload canceled"))
      } else {
        Future.successful(())
      }
    }

    val result = for {
      _ <- checkCanceled()
      sessions <- sessionManager.initializeSessions(workload, runningContainers)
      _ <- checkCanceled()
      _ = stateTracker.updateState(workload.id) {
        _.copy(
          sessions = Some(sessions)
        ).withWorkloadState(WorkloadStatus.Running)
      }
      _ <- checkCanceled()
      linkRunner = new LinkRunner(stateTracker, workload, sessions, metrics)
      _ <- checkCanceled()
      _ <- linkRunner.runLinks()
    } yield {
      ()
    }

    FutureHelper.andThenAsync(result) { _ =>
      payloadProvider.delete(workload.id)
    }
  }

  override def getEvaluation(id: String): Future[EvaluationWorkloadState] = {
    stateTracker.get(id) match {
      case None => Future.failed(new Errors.NotFoundException(s"Evaluation with id ${id} not found"))
      case Some(found) =>
        Future.successful(
          found.state
        )
    }
  }

  override def trackEvaluation(id: String): Source[EvaluationWorkloadState, Cancellable] = {
    stateTracker.track(id).map { evaluation =>
      evaluation.state
    }
  }

  override def cancelEvaluation(id: String): Future[Unit] = {
    stateTracker.updateEvaluationState(id) {
      _.copy(
        canceled = true
      )
    }
    stateTracker.get(id) match {
      case None => Future.failed(new Errors.NotFoundException(s"Evaluation with id ${id} not found"))
      case Some(found) =>
        workerManager.stopContainers(id)
    }
  }

  override def startDeployment(deploymentRequest: EvaluationDeploymentRequest): Future[EvaluationDeploymentResponse] = {
    deploymentManager.startDeployment(deploymentRequest)
  }

  override def removeDeployment(id: String): Future[Unit] = {
    deploymentManager.removeDeployment(id)
  }

  override def list(): Future[ListResponse] = {
    backend.listWorkers(ListWorkerRequest()).map { response =>
      // Fetching Data from backend
      val elements = response.workers.map { worker =>
        val kind = worker.`type` match {
          case WorkerType.MnpWorker   => ListElementKind.Deployment
          case WorkerType.MnpPipeline => ListElementKind.Evaluation
        }
        val status = worker.state match {
          case WorkerState.Pending               => WorkloadStatus.Preparing(None)
          case WorkerState.Running               => WorkloadStatus.Running
          case WorkerState.Failed(status, error) => WorkloadStatus.Failed(s"${error.mkString} (${status})")
          case WorkerState.Succeeded             => WorkloadStatus.Done
        }
        ListElement(
          id = worker.id,
          kind = kind,
          status = status
        )
      }

      // Deployments may be present as workers (for sub nodes) and as deployments (MnpPipelines)
      // We want them to be present only once
      val pipelineIds = elements.collect {
        case element if element.kind == ListElementKind.Deployment => element.id
      }.toSet

      val filtered = elements.filter { element =>
        element.kind == ListElementKind.Deployment || !pipelineIds.contains(element.id)
      }.distinct

      // Also fetching data from state (because it may be more current, but not ephemeral)
      val all = stateTracker.all().map { evaluation =>
        ListElement(evaluation.workload.id, ListElementKind.Evaluation, evaluation.state.status)
      }

      val presentInState = all.map(_.id).toSet

      val result = all ++ filtered.filterNot(element => presentInState.contains(element.id))

      ListResponse(result)
    }
  }
}

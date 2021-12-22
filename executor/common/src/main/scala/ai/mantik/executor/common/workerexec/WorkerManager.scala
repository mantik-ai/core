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

import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.model._
import ai.mantik.executor.Errors
import ai.mantik.executor.common.workerexec.model.{MnpWorkerDefinition, StartWorkerRequest, StopWorkerRequest}
import ai.mantik.mnp.protocol.mnp.AboutResponse
import ai.mantik.mnp.{MnpAddressUrl, MnpClient, MnpUrl}
import cats.implicits._

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** Responsible for bringing up Workers and connecting to them. */
class WorkerManager(
    state: StateTracker,
    backend: WorkerExecutorBackend,
    metrics: WorkerMetrics
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

  val mnpConnectionTimeout: FiniteDuration = config.getFiniteDuration("mantik.executor.worker.mnpConnectionTimeout")
  val mnpCloseConnectionTimeout: FiniteDuration =
    config.getFiniteDuration("mantik.executor.worker.mnpCloseConnectionTimeout")

  /** Prepare containers for an evaluation workload. */
  def prepareContainers(workload: EvaluationWorkload): Future[RunningContainers] = {
    val result = workload.containers.zipWithIndex
      .map { case (container, containerIdx) =>
        startContainer(workload.id, container).andThen {
          case Success(_) =>
            state.updateContainerState(workload.id, containerIdx) {
              _.copy(
                status = WorkloadStatus.Running
              )
            }
          case Failure(exception) =>
            state.updateContainerState(workload.id, containerIdx) {
              _.copy(
                status = WorkloadStatus.Failed(Option(exception.getMessage).getOrElse(s"Unknown"))
              )
            }
        }
      }
      .sequence
      .map { result =>
        RunningContainers(result)
      }

    result.andThen {
      case Success(_)     => // ok
      case Failure(error) =>
        // Kill containers in case of errors
        stopContainers(workload.id)
    }
  }

  private def startContainer(evaluationId: String, workloadContainer: WorkloadContainer): Future[ReservedContainer] = {
    val startWorkerRequest = StartWorkerRequest(
      id = evaluationId,
      definition = MnpWorkerDefinition(
        container = workloadContainer.container
      ),
      nameHint = workloadContainer.nameHint
    )

    val t0 = System.currentTimeMillis()
    for {
      response <- backend.startWorker(startWorkerRequest)
      (address, aboutResponse, mnpClient) <- buildConnection(response.internalUrl)
    } yield {
      val t1 = System.currentTimeMillis()
      logger.info(
        s"Spinned up worker ${response.nodeName} image=${workloadContainer.container.image} about=${aboutResponse.name} within ${t1 - t0}ms"
      )
      metrics.workersCreated.inc()
      metrics.workers.inc()
      ReservedContainer(
        response.nodeName,
        workloadContainer.container.image,
        address,
        mnpClient,
        aboutResponse
      )
    }
  }

  /**
    * Build a connection to the container.
    * @return address, about response, and mnp client.
    */
  private def buildConnection(
      internalUrl: String
  ): Future[(MnpAddressUrl, AboutResponse, MnpClient)] = {
    val address = MnpUrl.parse(internalUrl) match {
      case Right(ok: MnpAddressUrl) => ok
      case Left(bad) =>
        return Future.failed(new Errors.InternalException(s"Executor returned bad internal url: ${bad}"))
      case Right(other) =>
        return Future.failed(new Errors.InternalException(s"Executor returned url of wrong type: ${other}"))

    }
    backend.connectMnp(address.address).flatMap { client =>
      FutureHelper
        .tryMultipleTimes(mnpConnectionTimeout, 200.milliseconds) {
          client.about().transform {
            case Success(aboutResponse) => Success(Some(aboutResponse))
            case Failure(e)             => Success(None)
          }
        }
        .map { aboutResponse =>
          metrics.mnpConnections.inc()
          metrics.mnpConnectionsCreated.inc()
          (address, aboutResponse, client)
        }
    }
  }

  /** Shutdown created containers. */
  def shutdownContainers(evaluationId: String, runningContainers: RunningContainers): Future[Unit] = {
    for {
      _ <- closeMnpConnections(runningContainers).recover { case NonFatal(e) =>
        logger.warn(s"Error during closing MNP Connections", e)
      }
      _ <- stopContainers(evaluationId)
    } yield { () }
  }

  /** Close the connections to containers */
  private def closeMnpConnections(containerMapping: RunningContainers): Future[Unit] = {
    val shutdownFutures = containerMapping.containers.map { container =>
      closeMnpConnection(container)
    }.toSeq
    Future.sequence(shutdownFutures).map { _ => () }
  }

  /** Close the connection of a single Container */
  private def closeMnpConnection(reservedContainer: ReservedContainer): Future[Unit] = {
    Future {
      try {
        reservedContainer.mnpClient.channel.shutdownNow()
        reservedContainer.mnpClient.channel.awaitTermination(mnpCloseConnectionTimeout.toMillis, TimeUnit.MILLISECONDS)
      } catch {
        case NonFatal(e) =>
          logger.warn(s"Error on closing MNP Connection", e)
      } finally {
        metrics.mnpConnections.dec()
      }
    }
  }

  /** Stop all containers of a Job (hard way) */
  def stopContainers(evaluationId: String): Future[Unit] = {
    backend
      .stopWorker(
        StopWorkerRequest(
          idFilter = Some(evaluationId)
        )
      )
      .map { response =>
        logger.info(s"Stopped ${response.removed.size} workers")
        metrics.workers.dec(response.removed.size)
        ()
      }
  }
}

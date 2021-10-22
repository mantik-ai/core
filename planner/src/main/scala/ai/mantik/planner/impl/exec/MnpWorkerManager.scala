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
package ai.mantik.planner.impl.exec

import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.Executor
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{
  MnpPipelineDefinition,
  MnpWorkerDefinition,
  StartWorkerRequest,
  StartWorkerResponse,
  StopWorkerRequest
}
import ai.mantik.mnp.{MnpAddressUrl, MnpClient, MnpUrl}
import ai.mantik.mnp.protocol.mnp.AboutResponse
import ai.mantik.planner.PlanExecutor.PlanExecutorException
import ai.mantik.planner.impl.Metrics
import ai.mantik.planner.{Plan, PlanNodeService, PlanOp}
import akka.util.ByteString
import cats.implicits._
import io.grpc.ManagedChannel

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** Handles creation, connection and shutdown of regular Mnp Workers. */
@Singleton
private[planner] class MnpWorkerManager @Inject() (
    executor: Executor,
    metrics: Metrics
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {
  import MnpWorkerManager._

  val mnpConnectionTimeout: FiniteDuration = config.getFiniteDuration("mantik.planner.execution.mnpConnectionTimeout")
  val mnpCloseConnectionTimeout: FiniteDuration =
    config.getFiniteDuration("mantik.planner.execution.mnpCloseConnectionTimeout")

  /** Spins up containers needed for a plan. */
  def reserveContainers[T](jobId: String, plan: Plan[T]): Future[ContainerMapping] = {
    val containers = requiredContainersForPlan(plan)

    logger.info(s"Spinning up ${containers.size} containers")

    val containerReservations: Future[Vector[(Container, ReservedContainer)]] = containers
      .traverse { container =>
        startWorker(jobId, container).map { reservedContainer =>
          container -> reservedContainer
        }
      }

    val containerMapping = containerReservations.map { responses =>
      ContainerMapping(
        jobId = jobId,
        responses.toMap
      )
    }

    // If something fails kill all containers for the job jobId.
    containerMapping.recoverWith { case NonFatal(e) =>
      logger.error(s"Spinning up containers failed, trying to stop remaining ones", e)
      stopContainers(jobId).transformWith { _ =>
        Future.failed(e)
      }
    }
  }

  /** Figure out required containers for a given plan. */
  private def requiredContainersForPlan(plan: Plan[_]): Vector[Container] = {
    val runGraphs: Vector[PlanOp.RunGraph] = plan.op.foldLeftDown(
      Vector.empty[PlanOp.RunGraph]
    ) {
      case (v, op: PlanOp.RunGraph) => v :+ op
      case (v, _)                   => v
    }

    (for {
      runGraph <- runGraphs
      (_, node) <- runGraph.graph.nodes
      container <- node.service match {
        case c: PlanNodeService.DockerContainer => Some(c)
        case _                                  => None
      }
    } yield {
      container.container
    }).distinct
  }

  /** Close connection and remove containers. */
  def closeConnectionAndStopContainers(containerMapping: ContainerMapping): Future[Unit] = {
    for {
      _ <- closeMnpConnections(containerMapping).recover { case NonFatal(e) =>
        logger.warn(s"Error during closing MNP Connections", e)
      }
      _ <- stopContainers(containerMapping.jobId)
    } yield { () }
  }

  /** Close the connections to containers */
  private def closeMnpConnections(containerMapping: ContainerMapping): Future[Unit] = {
    val shutdownFutures = containerMapping.containers.map { case (_, reservedContainer) =>
      closeMnpConnection(reservedContainer)
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

  /** Stop all containers of a Job. */
  private def stopContainers(jobId: String): Future[Unit] = {
    executor
      .stopWorker(
        StopWorkerRequest(
          idFilter = Some(jobId)
        )
      )
      .map { response =>
        logger.info(s"Stopped ${response.removed.size} workers")
        metrics.workers.dec(response.removed.size)
        ()
      }
  }

  /** Spin up a container and creates a connection to it. */
  private def startWorker(jobId: String, container: Container): Future[ReservedContainer] = {
    val nameHint = "mantik-" + container.simpleImageName
    val startWorkerRequest = StartWorkerRequest(
      id = jobId,
      definition = MnpWorkerDefinition(
        container = container
      ),
      nameHint = Some(nameHint)
    )
    val t0 = System.currentTimeMillis()
    for {
      response <- executor.startWorker(startWorkerRequest)
      (address, aboutResponse, mnpClient) <- buildConnection(response.internalUrl)
    } yield {
      val t1 = System.currentTimeMillis()
      logger.info(
        s"Spinned up worker ${response.nodeName} image=${container.image} about=${aboutResponse.name} within ${t1 - t0}ms"
      )
      metrics.workersCreated.inc()
      metrics.workers.inc()
      ReservedContainer(
        response.nodeName,
        container.image,
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
      case Left(bad)                => return Future.failed(new PlanExecutorException(s"Executor returned bad internal url: ${bad}"))
      case Right(other) =>
        return Future.failed(new PlanExecutorException(s"Executor returned url of wrong type: ${other}"))

    }
    executor.connectMnp(address.address).flatMap { client =>
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

  /** Run a permanent worker. */
  def runPermanentWorker(
      id: String,
      nameHint: Option[String],
      container: Container,
      initializer: ByteString
  ): Future[StartWorkerResponse] = {
    val startWorkerRequest = StartWorkerRequest(
      id = id,
      definition = MnpWorkerDefinition(
        container = container,
        initializer = Some(initializer)
      ),
      nameHint = nameHint,
      keepRunning = true
    )
    executor.startWorker(startWorkerRequest).map { result =>
      metrics.permanentWorkersCreated.inc()
      result
    }
  }

  /** Run a permanent pipeline worker */
  def runPermanentPipeline(
      id: String,
      definition: MnpPipelineDefinition,
      ingressName: Option[String],
      nameHint: Option[String]
  ): Future[StartWorkerResponse] = {
    val startWorkerRequest = StartWorkerRequest(
      id = id,
      definition = definition,
      keepRunning = true,
      ingressName = ingressName,
      nameHint = nameHint
    )
    executor.startWorker(startWorkerRequest).map { result =>
      metrics.permanentPipelinesCreated.inc()
      result
    }
  }
}

private[planner] object MnpWorkerManager {

  case class ReservedContainer(
      name: String,
      image: String,
      mnpAddress: MnpAddressUrl,
      mnpClient: MnpClient,
      aboutResponse: AboutResponse
  )

  case class ContainerMapping(
      jobId: String,
      containers: Map[Container, ReservedContainer]
  )
}

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
import ai.mantik.executor.model.docker.{Container, DockerConfig}
import ai.mantik.executor.model.{
  GrpcProxy,
  MnpPipelineDefinition,
  MnpWorkerDefinition,
  StartWorkerRequest,
  StartWorkerResponse,
  StopWorkerRequest
}
import ai.mantik.mnp.MnpClient
import ai.mantik.mnp.protocol.mnp.AboutResponse
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

  val isolationSpace: String = config.getString("mantik.planner.isolationSpace")
  val dockerConfig: DockerConfig = DockerConfig.parseFromConfig(
    akkaRuntime.config.getConfig("mantik.bridge.docker")
  )

  val mnpConnectionTimeout: FiniteDuration = config.getFiniteDuration("mantik.planner.execution.mnpConnectionTimeout")
  val mnpCloseConnectionTimeout: FiniteDuration =
    config.getFiniteDuration("mantik.planner.execution.mnpCloseConnectionTimeout")
  val mnpPort: Int = config.getInt("mantik.planner.execution.mnpPort")

  /** Spins up containers needed for a plan. */
  def reserveContainers[T](jobId: String, plan: Plan[T]): Future[ContainerMapping] = {
    executor.grpcProxy(isolationSpace).flatMap { grpcProxy =>
      val containers = requiredContainersForPlan(plan)

      logger.info(s"Spinning up ${containers.size} containers")
      containers
        .traverse { container =>
          startWorker(grpcProxy, jobId, container).map { reservedContainer =>
            container -> reservedContainer
          }
        }
        .map { responses =>
          ContainerMapping(
            jobId = jobId,
            responses.toMap
          )
        }
        .recoverWith { case NonFatal(e) =>
          logger.error(s"Spinning up containers failed, trying to stop remaining ones", e)
          stopContainers(jobId).transformWith { _ =>
            Future.failed(e)
          }
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
      _ <- closeMnpConnections(containerMapping)
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
        reservedContainer.mnpChannel.shutdownNow()
        reservedContainer.mnpChannel.awaitTermination(mnpCloseConnectionTimeout.toMillis, TimeUnit.MILLISECONDS)
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
          isolationSpace,
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
  private def startWorker(grpcProxy: GrpcProxy, jobId: String, container: Container): Future[ReservedContainer] = {
    val nameHint = "mantik-" + container.simpleImageName
    val startWorkerRequest = StartWorkerRequest(
      isolationSpace,
      id = jobId,
      definition = MnpWorkerDefinition(
        container = container,
        extraLogins = dockerConfig.logins
      ),
      nameHint = Some(nameHint)
    )
    val t0 = System.currentTimeMillis()
    for {
      response <- executor.startWorker(startWorkerRequest)
      (address, aboutResponse, channel, mnpClient) <- buildConnection(grpcProxy, response.nodeName)
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
        channel,
        mnpClient,
        aboutResponse
      )
    }
  }

  /**
    * Build a connection to the container.
    * @return address, about response, mnp channel and mnp client.
    */
  private def buildConnection(
      grpcProxy: GrpcProxy,
      nodeName: String
  ): Future[(String, AboutResponse, ManagedChannel, MnpClient)] = {
    val address = s"${nodeName}:${mnpPort}"
    Future {
      grpcProxy.proxyUrl match {
        case Some(proxy) => MnpClient.connectViaProxy(proxy, address)
        case None        => MnpClient.connect(address)
      }
    }.flatMap { case (channel, client) =>
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
          (address, aboutResponse, channel, client)
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
      isolationSpace,
      id = id,
      definition = MnpWorkerDefinition(
        container = container,
        extraLogins = dockerConfig.logins,
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
      isolationSpace,
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
      address: String,
      mnpChannel: ManagedChannel,
      mnpClient: MnpClient,
      aboutResponse: AboutResponse
  )

  case class ContainerMapping(
      jobId: String,
      containers: Map[Container, ReservedContainer]
  )
}

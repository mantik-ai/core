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
package ai.mantik.engine

import ai.mantik.componently.{AkkaRuntime, Component}
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutServiceBlockingStub
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderServiceBlockingStub
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorServiceBlockingStub
import ai.mantik.engine.protos.local_registry.LocalRegistryServiceGrpc.LocalRegistryServiceStub
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionServiceBlockingStub
import ai.mantik.planner.PlanningContext
import ai.mantik.planner.impl.RemotePlanningContextImpl
import ai.mantik.planner.protos.planning_context.PlanningContextServiceGrpc.PlanningContextServiceStub
import com.google.protobuf.empty.Empty
import com.typesafe.scalalogging.Logger
import io.grpc.Status.Code
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}

import scala.concurrent.Future

/** Talks to a local engine. */
class EngineClient(address: String)(implicit val akkaRuntime: AkkaRuntime) extends Component {
  val logger = Logger(getClass)
  logger.info(s"Connecting to Mantik Engine at ${address}")

  val channel: ManagedChannel = ManagedChannelBuilder.forTarget(address).usePlaintext().build()

  val aboutService = new AboutServiceBlockingStub(channel)

  val version =
    try {
      aboutService.version(Empty())
    } catch {
      case e: StatusRuntimeException if e.getStatus.getCode == Code.UNAVAILABLE =>
        logger.error("Could not connect to Mantik Engine, is the service running?!")
        throw e
    }
  logger.info(s"Connected to Mantik Engine ${version.version}")

  val sessionService = new SessionServiceBlockingStub(channel)
  val graphBuilder = new GraphBuilderServiceBlockingStub(channel)
  val graphExecutor = new GraphExecutorServiceBlockingStub(channel)
  val localRegistryService = new LocalRegistryServiceStub(channel)
  val planningContextStub = new PlanningContextServiceStub(channel)

  val planningContext: PlanningContext = {
    new RemotePlanningContextImpl(planningContextStub)
  }

  akkaRuntime.lifecycle.addShutdownHook {
    channel.shutdownNow()
    Future.successful(())
  }
}

object EngineClient {
  def create()(implicit akkaRuntime: AkkaRuntime): EngineClient = {
    val port = akkaRuntime.config.getInt("mantik.engine.server.port")
    val interface = akkaRuntime.config.getString("mantik.engine.server.interface")
    val full = s"${interface}:${port}"
    new EngineClient(full)
  }
}

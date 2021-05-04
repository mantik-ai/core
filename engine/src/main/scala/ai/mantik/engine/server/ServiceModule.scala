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
package ai.mantik.engine.server

import ai.mantik.engine.protos.debug.DebugServiceGrpc.DebugService
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderService
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorService
import ai.mantik.engine.protos.local_registry.LocalRegistryServiceGrpc.LocalRegistryService
import ai.mantik.engine.protos.remote_registry.RemoteRegistryServiceGrpc.RemoteRegistryService
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionService
import ai.mantik.engine.server.services._
import ai.mantik.engine.session.{SessionManager, SessionManagerForLocalRunning}
import ai.mantik.planner.impl.RemotePlanningContextServerImpl
import ai.mantik.planner.protos.planning_context.PlanningContextServiceGrpc.PlanningContextService
import com.google.inject.AbstractModule

object ServiceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[SessionManager]).to(classOf[SessionManagerForLocalRunning])
    bind(classOf[DebugService]).to(classOf[DebugServiceImpl])
    bind(classOf[AboutService]).to(classOf[AboutServiceImpl])
    bind(classOf[GraphBuilderService]).to(classOf[GraphBuilderServiceImpl])
    bind(classOf[GraphExecutorService]).to(classOf[GraphExecutorServiceImpl])
    bind(classOf[SessionService]).to(classOf[SessionServiceImpl])
    bind(classOf[LocalRegistryService]).to(classOf[LocalRegistryServiceImpl])
    bind(classOf[RemoteRegistryService]).to(classOf[RemoteRegistryServiceImpl])
    bind(classOf[PlanningContextService]).to(classOf[RemotePlanningContextServerImpl])
  }
}

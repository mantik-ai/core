package ai.mantik.engine.server

import ai.mantik.engine.protos.debug.DebugServiceGrpc.DebugService
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderService
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorService
import ai.mantik.engine.protos.local_registry.LocalRegistryServiceGrpc.LocalRegistryService
import ai.mantik.engine.protos.remote_registry.RemoteRegistryServiceGrpc.RemoteRegistryService
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionService
import ai.mantik.engine.server.services._
import ai.mantik.engine.session.{ SessionManager, SessionManagerForLocalRunning }
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

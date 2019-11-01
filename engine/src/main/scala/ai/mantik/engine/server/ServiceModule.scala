package ai.mantik.engine.server

import ai.mantik.engine.protos.debug.DebugServiceGrpc.DebugService
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderService
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorService
import ai.mantik.engine.protos.local_registry.LocalRegistryServiceGrpc.LocalRegistryService
import ai.mantik.engine.protos.remote_registry.RemoteRegistryServiceGrpc.RemoteRegistryService
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionService
import ai.mantik.engine.server.services.{ AboutServiceImpl, DebugServiceImpl, GraphBuilderServiceImpl, GraphExecutorServiceImpl, LocalRegistryServiceImpl, RemoteRegistryServiceImpl, SessionServiceImpl }
import ai.mantik.engine.session.{ Session, SessionManager, SessionManagerForLocalRunning }
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc.FileRepositoryService
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryService
import ai.mantik.planner.repository.rpc.{ FileRepositoryServiceImpl, RepositoryServiceImpl }
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
    bind(classOf[FileRepositoryService]).to(classOf[FileRepositoryServiceImpl])
    bind(classOf[RepositoryService]).to(classOf[RepositoryServiceImpl])
    bind(classOf[RemoteRegistryService]).to(classOf[RemoteRegistryServiceImpl])
  }
}

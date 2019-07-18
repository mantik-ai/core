package ai.mantik.engine

import ai.mantik.componently.AkkaRuntime
import ai.mantik.engine.server.EngineServer
import ai.mantik.engine.server.services.{ AboutServiceImpl, DebugServiceImpl, GraphBuilderServiceImpl, GraphExecutorServiceImpl, SessionServiceImpl }
import ai.mantik.engine.session.{ Session, SessionManager }
import ai.mantik.executor.kubernetes.KubernetesExecutor
import ai.mantik.executor.server.{ ExecutorServer, ServerConfig }
import ai.mantik.planner.impl.ContextImpl
import ai.mantik.planner.repository.rpc.{ FileRepositoryServiceImpl, RepositoryServiceImpl }
import ai.mantik.planner.repository.{ FileRepository, Repository }
import ai.mantik.planner.{ Context, CoreComponents, PlanExecutor, Planner }

/**
 * Builds and initializes the Engine process.
 * To be used as long as there is no DI #86
 */
private[engine] object EngineFactory {

  def makeEngineContext()(implicit akkaRuntime: AkkaRuntime): Context = {
    val fileRepository = FileRepository.createFileRepository()
    val repository = Repository.create()
    val executor = KubernetesExecutor.create(akkaRuntime.config)
    val executorServerConfig = ServerConfig.fromTypesafe(akkaRuntime.config)
    val executorServer = new ExecutorServer(executorServerConfig, executor)
    executorServer.start()
    ContextImpl.constructWithComponents(
      repository,
      fileRepository,
      executor,
      shutdownMethod = () => {
        executorServer.stop()
      }
    )
  }

  /** Build an Engine Server. */
  def makeEngineServer(context: Context)(implicit akkaRuntime: AkkaRuntime): EngineServer = {
    implicit val implicitConfig = akkaRuntime.config
    implicit val ec = akkaRuntime.executionContext
    val sessionManager = new SessionManager[Session]({ id =>
      new Session(id, createViewForSession(context))
    })
    val aboutService = new AboutServiceImpl()
    val sessionService = new SessionServiceImpl(sessionManager)
    val graphBuilderService = new GraphBuilderServiceImpl(sessionManager)
    val graphExecutorService = new GraphExecutorServiceImpl(sessionManager)
    val debugService = new DebugServiceImpl(context)
    val repositoryService = new RepositoryServiceImpl(context.repository)
    val fileRepositoryService = new FileRepositoryServiceImpl(context.fileRepository)
    val server = new EngineServer(
      aboutService,
      sessionService,
      graphBuilderService,
      graphExecutorService,
      debugService,
      repositoryService,
      fileRepositoryService
    )
    server
  }

  /** Create a view on to the context for a session (effectivly disabling the shutdown method). */
  private def createViewForSession(context: Context): CoreComponents = {
    new CoreComponents {
      override def fileRepository: FileRepository = context.fileRepository

      override def repository: Repository = context.repository

      override def planner: Planner = context.planner

      override def planExecutor: PlanExecutor = context.planExecutor

      override def shutdown(): Unit = {} // disabled
    }
  }
}

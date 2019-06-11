package ai.mantik.engine

import ai.mantik.engine.server.EngineServer
import ai.mantik.engine.server.services.{ AboutServiceImpl, DebugServiceImpl, GraphBuilderServiceImpl, GraphExecutorServiceImpl, SessionServiceImpl }
import ai.mantik.engine.session.{ Session, SessionManager }
import ai.mantik.planner.{ Context, CoreComponents, PlanExecutor, Planner }
import ai.mantik.repository.{ FileRepository, Repository }
import akka.stream.Materializer
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

/**
 * Builds and initializes the Engine process.
 * To be used as long as there is no DI #86
 */
object EngineFactory {

  /** Build an Engine Server. */
  def makeEngineServer(config: Config, context: Context)(implicit ec: ExecutionContext, materializer: Materializer): EngineServer = {
    implicit val implicitConfig = config
    val sessionManager = new SessionManager[Session]({ id =>
      new Session(id, createViewForSession(context))
    })
    val aboutService = new AboutServiceImpl()
    val sessionService = new SessionServiceImpl(sessionManager)
    val graphBuilderService = new GraphBuilderServiceImpl(sessionManager)
    val graphExecutorService = new GraphExecutorServiceImpl(sessionManager)
    val debugService = new DebugServiceImpl(context)
    val server = new EngineServer(
      aboutService,
      sessionService,
      graphBuilderService,
      graphExecutorService,
      debugService
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

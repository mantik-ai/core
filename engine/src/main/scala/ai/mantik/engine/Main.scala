package ai.mantik.engine

import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.engine.server.EngineServer
import ai.mantik.engine.server.services.{ AboutServiceImpl, DebugServiceImpl, GraphBuilderServiceImpl, GraphExecutorServiceImpl, SessionServiceImpl }
import ai.mantik.engine.session.{ Session, SessionManager }
import ai.mantik.planner.{ Context, CoreComponents, PlanExecutor, Planner }
import ai.mantik.repository.{ FileRepository, Repository }
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object Main {
  private val logger = LoggerFactory.getLogger(getClass)

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

  def main(args: Array[String]): Unit = {
    logger.info(s"Initializing Mantik Engine ${BuildInfo}")
    implicit val actorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val config = ConfigFactory.load()
    implicit val ec: ExecutionContext = actorSystem.dispatcher

    val context = Context.localWithAkka()
    try {
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
      server.start()
      server.waitUntilFinished()
    } catch {
      case e: Exception =>
        logger.error("Error ", e)
    } finally {
      context.shutdown()
    }

  }
}

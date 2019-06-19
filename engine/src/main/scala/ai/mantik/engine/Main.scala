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

  def main(args: Array[String]): Unit = {
    logger.info(s"Initializing Mantik Engine ${BuildInfo}")
    implicit val actorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    val config = ConfigFactory.load()

    val context = Context.localWithAkka(config)
    try {
      val server = EngineFactory.makeEngineServer(config, context)
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

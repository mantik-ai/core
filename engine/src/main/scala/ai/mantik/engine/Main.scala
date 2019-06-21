package ai.mantik.engine

import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.planner.Context
import ai.mantik.planner.utils.AkkaRuntime
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
    implicit val akkaRuntime = AkkaRuntime.fromRunning()

    val context = Context.localWithAkka()
    try {
      val server = EngineFactory.makeEngineServer(context)
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

package ai.mantik.executor

import java.time.Clock

import ai.mantik.executor.buildinfo.BuildInfo
import ai.mantik.executor.kubernetes.{ Config, K8sOperations, KubernetesExecutor }
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import ai.mantik.executor.server.{ ExecutorServer, ServerConfig }
import com.typesafe.config.ConfigFactory

object Main extends App {
  val logger = Logger(getClass)
  logger.info(s"Starting Mantik Executor ${BuildInfo.version} (${BuildInfo.gitVersion} ${BuildInfo.buildNum})")

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  implicit val clock = Clock.systemUTC()
  try {
    val typesafeConfig = ConfigFactory.load()
    val executor = KubernetesExecutor.create(typesafeConfig)
    val serverConfig = ServerConfig.fromTypesafe(typesafeConfig)
    val server = new ExecutorServer(serverConfig, executor)
    logger.info("Starting Executor Server")
    server.start()
  } catch {
    case e: Exception =>
      logger.error("Could not start executor", e)
      System.exit(1)
  }
}

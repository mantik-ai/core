package ai.mantik.executor

import java.time.Clock

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.buildinfo.BuildInfo
import ai.mantik.executor.kubernetes.{ KubernetesExecutor }
import com.typesafe.scalalogging.Logger
import ai.mantik.executor.server.{ ExecutorServer, ServerConfig }
import com.typesafe.config.ConfigFactory

object Main extends App {
  val logger = Logger(getClass)
  logger.info(s"Starting Mantik Executor ${BuildInfo.version} (${BuildInfo.gitVersion} ${BuildInfo.buildNum})")

  implicit val akkaRuntime = AkkaRuntime.createNew()
  try {
    val executor = KubernetesExecutor.create(akkaRuntime.config)
    val serverConfig = ServerConfig.fromTypesafe(akkaRuntime.config)
    val server = new ExecutorServer(serverConfig, executor)
    logger.info("Starting Executor Server")
    server.start()
  } catch {
    case e: Exception =>
      logger.error("Could not start executor", e)
      System.exit(1)
  }
}

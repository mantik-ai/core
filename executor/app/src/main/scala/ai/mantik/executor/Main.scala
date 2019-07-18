package ai.mantik.executor

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.AkkaModule
import ai.mantik.executor.buildinfo.BuildInfo
import com.typesafe.scalalogging.Logger
import ai.mantik.executor.server.{ ExecutorServer, ServerConfig }
import com.google.inject.{ Guice, Injector }

object Main extends App {
  val logger = Logger(getClass)
  logger.info(s"Starting Mantik Executor ${BuildInfo.version} (${BuildInfo.gitVersion} ${BuildInfo.buildNum})")

  implicit val akkaRuntime = AkkaRuntime.createNew()
  try {
    val injector = Guice.createInjector(
      new AkkaModule(),
      new ExecutorModule()
    )
    val executor = injector.getInstance(classOf[Executor])
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

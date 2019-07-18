package ai.mantik.engine

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.AkkaModule
import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.engine.server.{ EngineServer, ServiceModule }
import com.google.inject.Guice
import org.slf4j.LoggerFactory

object Main {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logger.info(s"Initializing Mantik Engine ${BuildInfo}")
    implicit val akkaRuntime = AkkaRuntime.createNew()

    try {
      val injector = Guice.createInjector(
        new AkkaModule(),
        new EngineModule(),
        ServiceModule
      )

      val server = injector.getInstance(classOf[EngineServer])
      server.start()
      server.waitUntilFinished()
    } catch {
      case e: Exception =>
        logger.error("Error ", e)
        System.exit(1)
    } finally {
      System.exit(0)
    }

  }
}

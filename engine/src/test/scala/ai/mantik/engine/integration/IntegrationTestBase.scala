package ai.mantik.engine.integration

import ai.mantik.componently
import ai.mantik.componently.{ AkkaRuntime, Lifecycle }
import ai.mantik.componently.di.AkkaModule
import ai.mantik.engine.{ EngineClient, EngineModule }
import ai.mantik.engine.server.{ EngineServer, ServiceModule }
import ai.mantik.planner.Context
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import com.google.inject.Guice
import com.typesafe.config.{ Config, ConfigFactory }

/** Base classes for integration tests. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport {

  protected var context: Context = _
  protected var engineServer: EngineServer = _
  protected var engineClient: EngineClient = _

  override protected lazy val typesafeConfig: Config = ConfigFactory.load("systemtest.conf")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    implicit val akkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)
    val injector = Guice.createInjector(
      new AkkaModule(),
      ServiceModule,
      new EngineModule(clientConfig = None)
    )
    engineServer = injector.getInstance(classOf[EngineServer])
    engineServer.start()
    engineClient = new EngineClient(s"localhost:${engineServer.port}")
    context = engineClient.createContext()
  }
}

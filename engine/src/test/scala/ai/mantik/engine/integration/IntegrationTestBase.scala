package ai.mantik.engine.integration

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.AkkaModule
import ai.mantik.engine.{ EngineClient, EngineModule }
import ai.mantik.engine.server.{ EngineServer, ServiceModule, ShutdownHelper }
import ai.mantik.planner.Context
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import com.google.inject.Guice
import com.typesafe.config.{ Config, ConfigFactory }

/** Base classes for integration tests. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport {

  protected var context: Context = _
  protected var engineServer: EngineServer = _
  protected var engineClient: EngineClient = _
  protected var shutdownHelper: ShutdownHelper = _

  override protected lazy val typesafeConfig: Config = ConfigFactory.load("systemtest.conf")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    implicit val akkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)
    val injector = Guice.createInjector(
      new AkkaModule(),
      ServiceModule,
      new EngineModule()
    )
    engineServer = injector.getInstance(classOf[EngineServer])
    shutdownHelper = injector.getInstance(classOf[ShutdownHelper])
    engineServer.start()
    engineClient = new EngineClient(s"localhost:${engineServer.port}")
    context = engineClient.createContext()
  }

  override protected def afterAll(): Unit = {
    engineClient.shutdown()
    engineServer.shutdown()
    shutdownHelper.shutdown()
    super.afterAll()
  }
}

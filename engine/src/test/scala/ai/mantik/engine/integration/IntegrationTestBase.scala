package ai.mantik.engine.integration

import ai.mantik.engine.{ EngineClient, EngineFactory }
import ai.mantik.engine.server.EngineServer
import ai.mantik.executor.kubernetes.ExecutorForIntegrationTests
import ai.mantik.planner.Context
import ai.mantik.planner.impl.ContextImpl
import ai.mantik.planner.utils.AkkaRuntime
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import com.typesafe.config.{ Config, ConfigFactory }

/** Base classes for integration tests. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport {

  protected var embeddedExecutor: ExecutorForIntegrationTests = _
  protected var context: Context = _
  protected var engineServer: EngineServer = _
  protected var engineClient: EngineClient = _

  override protected lazy val typesafeConfig: Config = ConfigFactory.load("systemtest.conf")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    embeddedExecutor = new ExecutorForIntegrationTests(typesafeConfig)
    implicit val akkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)
    context = ContextImpl.constructForLocalTestingWithAkka()
    engineServer = EngineFactory.makeEngineServer(context)
    engineServer.start()
    engineClient = new EngineClient(s"localhost:${engineServer.port}")
  }

  override protected def afterAll(): Unit = {
    engineClient.shutdown()
    engineServer.stop()
    embeddedExecutor.shutdown()
    context.shutdown()
    super.afterAll()
  }
}

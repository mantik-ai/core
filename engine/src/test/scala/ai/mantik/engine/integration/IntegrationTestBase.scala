package ai.mantik.engine.integration

import ai.mantik.engine.EngineFactory
import ai.mantik.engine.server.EngineServer
import ai.mantik.executor.ExecutorForIntegrationTests
import ai.mantik.planner.Context
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }

/** Base classes for integration tests. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport {

  protected var embeddedExecutor: ExecutorForIntegrationTests = _
  protected var context: Context = _
  protected var engineServer: EngineServer = _
  protected var engineClient: EngineClient = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = ConfigFactory.load()
      .withValue("mantik.repository.type", ConfigValueFactory.fromAnyRef("temp"))
    embeddedExecutor = new ExecutorForIntegrationTests()
    context = Context.localWithAkka(config)
    engineServer = EngineFactory.makeEngineServer(config, context)
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

package ai.mantik.planner.integration

import ai.mantik.executor.{ Config, ExecutorForIntegrationTests }
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import ai.mantik.planner.Context
import com.typesafe.config.ConfigFactory

/** Base class for integration tests having a full running executor instance. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport {

  protected var embeddedExecutor: ExecutorForIntegrationTests = _
  protected val config = ConfigFactory.load()
  protected var context: Context = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    embeddedExecutor = new ExecutorForIntegrationTests()
    context = Context.localWithAkka(config)
  }

  override protected def afterAll(): Unit = {
    embeddedExecutor.shutdown()
    context.shutdown()
    super.afterAll()
  }
}

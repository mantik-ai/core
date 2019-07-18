package ai.mantik.planner.util

import ai.mantik.componently.AkkaRuntime
import ai.mantik.testutils.{ AkkaSupport, FakeClock, TestBase }
import com.typesafe.config.ConfigFactory

/** Test base which already initializes the AkkaRuntime. */
abstract class TestBaseWithAkkaRuntime extends TestBase with AkkaSupport {

  val fakeClock = new FakeClock()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    fakeClock.resetTime()
  }

  protected def config = ConfigFactory.load()

  protected implicit def akkaRuntime: AkkaRuntime = AkkaRuntime.fromRunning(config, fakeClock)
}

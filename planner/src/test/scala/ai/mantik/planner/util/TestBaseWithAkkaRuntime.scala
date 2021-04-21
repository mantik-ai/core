package ai.mantik.planner.util

import ai.mantik.componently.utils.GlobalLocalAkkaRuntime
import ai.mantik.testutils.{AkkaSupport, FakeClock, TestBase}

/** Test base which already initializes the AkkaRuntime. */
abstract class TestBaseWithAkkaRuntime extends TestBase with AkkaSupport with GlobalLocalAkkaRuntime {

  override val clock: FakeClock = new FakeClock()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    clock.resetTime()
    enterTestcase()
  }

  override protected def afterEach(): Unit = {
    exitTestcase()
  }
}

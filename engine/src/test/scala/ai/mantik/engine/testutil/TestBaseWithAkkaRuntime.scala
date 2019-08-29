package ai.mantik.engine.testutil

import ai.mantik.componently.utils.GlobalLocalAkkaRuntime
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import com.typesafe.config.{ Config, ConfigFactory }

abstract class TestBaseWithAkkaRuntime extends TestBase with AkkaSupport with GlobalLocalAkkaRuntime {
  protected def config: Config = ConfigFactory.load()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    enterTestcase()
  }

  override protected def afterEach(): Unit = {
    exitTestcase()
    super.afterEach()
  }
}

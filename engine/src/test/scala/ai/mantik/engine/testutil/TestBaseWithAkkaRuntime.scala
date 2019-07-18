package ai.mantik.engine.testutil

import ai.mantik.componently.AkkaRuntime
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import com.typesafe.config.{ Config, ConfigFactory }

abstract class TestBaseWithAkkaRuntime extends TestBase with AkkaSupport {
  protected def config: Config = ConfigFactory.load()
  protected implicit def akkaRuntime: AkkaRuntime = AkkaRuntime.fromRunning(config)
}

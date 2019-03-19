package io.mantik.executor

import com.typesafe.config.ConfigFactory
import io.mantik.executor.testutils.TestBase

class ConfigSpec extends TestBase {

  it should "parse with default packaged values" in {
    val config = Config.fromTypesafeConfig(ConfigFactory.defaultApplication())
    config.port shouldBe 8080
    config.interface shouldBe "0.0.0.0"
  }
}

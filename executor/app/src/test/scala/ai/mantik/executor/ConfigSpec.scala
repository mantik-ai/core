package ai.mantik.executor

import com.typesafe.config.ConfigFactory
import ai.mantik.testutils.TestBase

class ConfigSpec extends TestBase {

  it should "parse with default packaged values" in {
    val config = Config.fromTypesafeConfig(ConfigFactory.load())
    config.port shouldBe 8085
    config.interface shouldBe "0.0.0.0"
    config.coordinator.image should startWith(config.dockerConfig.defaultImageRepository.get)
  }
}

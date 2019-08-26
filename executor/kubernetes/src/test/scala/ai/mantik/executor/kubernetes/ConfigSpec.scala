package ai.mantik.executor.kubernetes

import ai.mantik.testutils.TestBase

class ConfigSpec extends TestBase {

  it should "parse with default packaged values" in {
    val config = Config.fromTypesafeConfig(typesafeConfig)
    config.common.coordinator.image should startWith(config.dockerConfig.defaultImageRepository.get)
  }
}

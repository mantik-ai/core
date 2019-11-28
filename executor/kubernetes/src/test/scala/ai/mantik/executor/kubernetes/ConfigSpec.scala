package ai.mantik.executor.kubernetes

import ai.mantik.testutils.TestBase

class ConfigSpec extends TestBase {

  it should "parse with default packaged values" in {
    Config.fromTypesafeConfig(typesafeConfig)
  }
}

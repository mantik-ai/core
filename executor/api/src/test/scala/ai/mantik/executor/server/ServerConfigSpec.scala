package ai.mantik.executor.server

import ai.mantik.testutils.TestBase

class ServerConfigSpec extends TestBase {

  it should "parse the default config" in {
    val config = ServerConfig.fromTypesafe(typesafeConfig)
    config.port shouldBe 8085
    config.interface shouldBe "0.0.0.0"
  }
}

package ai.mantik.planner.bridge

import ai.mantik.testutils.TestBase
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }

class BridgesImplSpec extends TestBase {

  trait Env {
    val bridgeLoader = new BridgeLoader(ConfigFactory.load())
    val bridges = bridgeLoader.toBridges
  }

  it should "parse the default config" in new Env {
    bridgeLoader.trainableAlgorithms shouldNot be(empty)
    bridgeLoader.algorithms shouldNot be(empty)
    bridgeLoader.formats shouldNot be(empty)
    bridgeLoader.dockerConfig.defaultImageRepository shouldBe (defined)
    bridgeLoader.dockerConfig.defaultImageTag shouldBe (defined)

    bridges.algorithmBridge(bridgeLoader.algorithms.head.stack) shouldBe
      Some(bridgeLoader.algorithms.head)

    bridges.trainableAlgorithmBridge(bridgeLoader.trainableAlgorithms.head.stack) shouldBe
      Some(bridgeLoader.trainableAlgorithms.head)

    bridges.formatBridge(bridgeLoader.formats.head.format) shouldBe
      Some(bridgeLoader.formats.head)
  }

  it should "contain natural" in new Env {
    bridges.formatBridge("natural") shouldBe Some(FormatBridge("natural", None))
  }

  "bridges" should "do resolving" in new Env {
    val config = ConfigFactory.load().withValue(
      "mantik.bridge.docker.defaultImageTag", ConfigValueFactory.fromAnyRef("tag1")
    ).withValue(
        "mantik.bridge.docker.defaultImageRepository", ConfigValueFactory.fromAnyRef("repo1")
      )
    val loader = new BridgeLoader(config)
    val algorithm1 = loader.algorithms.head
    algorithm1.container.image should startWith("repo1/")
    algorithm1.container.image should endWith("tag1")
  }
}

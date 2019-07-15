package ai.mantik.executor.kubernetes

import ai.mantik.testutils.TestBase

class KubernetesNamerSpec extends TestBase {

  it should "work in an easy example" in {
    val namer = new KubernetesNamer("id1", "prefix-")

    namer.configName shouldBe "prefix-id1-config"
    namer.jobName shouldBe "prefix-id1-job"
    namer.replicaSetName shouldBe "prefix-id1-rs"
    namer.serviceName shouldBe "prefix-id1-service"
    namer.podName("A") shouldBe "prefix-id1-a"
    namer.podName("b") shouldBe "prefix-id1-b"
    namer.podName("A") shouldBe "prefix-id1-a"
    namer.podName(":+-4") shouldBe "prefix-id1--4"
    namer.podName("config") shouldBe "prefix-id1-config0"
    namer.podName("Config") shouldBe "prefix-id1-config1"
    namer.podName("Config") shouldBe "prefix-id1-config1"
    namer.podName("config") shouldBe "prefix-id1-config0"
  }

  it should "escape labels" in {
    val pairs = Seq(
      "" -> "",
      "AbC120d" -> "AbC120d",
      "my.domain" -> "my.domain",
      "." -> "Z002e",
      "Z" -> "Z_",
      "Z." -> "Z_.",
      "Z/" -> "Z__002f",
      "__" -> "Z005f_005f",
      "Z_." -> "Z__005f."
    )
    pairs.foreach {
      case (from, to) =>
        KubernetesNamer.encodeLabelValue(from) shouldBe to
    }
    pairs.foreach {
      case (from, to) =>
        KubernetesNamer.decodeLabelValue(to) shouldBe from
    }
  }
}

package ai.mantik.executor.impl

import ai.mantik.testutils.TestBase

class KubernetesNamerSpec extends TestBase {

  it should "work in an easy example" in {
    val namer = new KubernetesNamer("id1")

    namer.configName shouldBe "job-id1-config"
    namer.jobName shouldBe "job-id1-job"
    namer.podName("A") shouldBe "job-id1-a"
    namer.podName("b") shouldBe "job-id1-b"
    namer.podName("A") shouldBe "job-id1-a"
    namer.podName(":+-4") shouldBe "job-id1--4"
    namer.podName("config") shouldBe "job-id1-config0"
    namer.podName("Config") shouldBe "job-id1-config1"
    namer.podName("Config") shouldBe "job-id1-config1"
    namer.podName("config") shouldBe "job-id1-config0"
  }
}

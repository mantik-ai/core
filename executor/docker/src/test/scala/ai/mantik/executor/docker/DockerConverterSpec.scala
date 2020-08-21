package ai.mantik.executor.docker

import ai.mantik.executor.common.CommonConfig
import ai.mantik.executor.docker.api.PullPolicy
import ai.mantik.executor.model.docker.Container
import ai.mantik.testutils.TestBase

class DockerConverterSpec extends TestBase {

  trait Env {
    val defaultConfig = DockerExecutorConfig.fromTypesafeConfig(typesafeConfig)
    lazy val commonConfig = defaultConfig.common
    lazy val config = defaultConfig.copy(
      common = commonConfig
    )
    lazy val defaultLabels = Map("foo" -> "bar")
    lazy val converter = new DockerConverter(config, "isolation1", "internalId", "userId")
  }

  "pullPolicy" should "work when pulling is disabled" in new Env {
    override lazy val commonConfig: CommonConfig = defaultConfig.common.copy(
      disablePull = true
    )
    converter.pullPolicy(
      Container("foo")
    ) shouldBe PullPolicy.Never

    converter.pullPolicy(
      Container("foo:latest")
    ) shouldBe PullPolicy.Never
  }

  it should "give out nice defaults" in new Env {
    require(!commonConfig.disablePull)
    converter.pullPolicy(
      Container("foo")
    ) shouldBe PullPolicy.Always

    converter.pullPolicy(
      Container("foo:latest")
    ) shouldBe PullPolicy.Always

    converter.pullPolicy(
      Container("foo:other")
    ) shouldBe PullPolicy.IfNotPresent
  }
}

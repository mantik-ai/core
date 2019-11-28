package ai.mantik.executor.docker

import ai.mantik.executor.common.CommonConfig
import ai.mantik.executor.docker.api.PullPolicy
import ai.mantik.executor.model.{ ContainerService, DataProvider }
import ai.mantik.executor.model.docker.Container
import ai.mantik.testutils.TestBase

class DockerConverterSpec extends TestBase {

  trait Env {
    val defaultConfig = DockerExecutorConfig.fromTypesafeConfig(typesafeConfig)
    lazy val commonConfig = defaultConfig.common
    lazy val config = DockerExecutorConfig(
      common = commonConfig,
      ingress = defaultConfig.ingress
    )
    lazy val defaultLabels = Map("foo" -> "bar")
    lazy val converter = new DockerConverter(config, defaultLabels)
  }

  val node1 = ContainerService(
    main = Container(
      image = "foo",
      parameters = Seq("a", "b")
    ),
    port = 100,
    dataProvider = Some(
      DataProvider(
        url = Some("my_url"),
        mantikfile = Some("mantikfile...")
      )
    )
  )

  "worker" should "translate a worker container" in new Env {
    val result = converter.generateWorkerContainer("node1", node1).run(NameGenerator("root")).value._2
    result.name shouldBe "root-node1"
    result.mainPort shouldBe Some(100)
    checkMapContains(result.createRequest.Labels, defaultLabels)
    result.createRequest.Labels(DockerConstants.RoleLabelName) shouldBe DockerConstants.WorkerRole
    result.createRequest.Labels(DockerConstants.PortLabel) shouldBe "100"
    result.createRequest.HostConfig.VolumesFrom shouldBe Vector("root-node1-pp:rw")
  }

  "generatePayloadProvider" should "translate a payload provider" in new Env {
    val result = converter.generatePayloadProvider("node1", node1.dataProvider.get).run(NameGenerator("root")).value._2
    result.name shouldBe "root-node1-pp"
    result.mainPort shouldBe None
    checkMapContains(result.createRequest.Labels, defaultLabels)
    result.createRequest.Labels(DockerConstants.RoleLabelName) shouldBe DockerConstants.PayloadProviderRole
  }

  private def checkMapContains(from: Map[String, String], expected: Map[String, String]): Unit = {
    expected.foreach {
      case (k, v) =>
        from.get(k) shouldBe Some(v)
    }
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

package ai.mantik.executor.impl

import java.nio.charset.StandardCharsets

import ai.mantik.executor.Config
import ai.mantik.executor.model.{ ContainerService, DataProvider, Node }
import ai.mantik.executor.model.docker.{ Container, DockerConfig, DockerLogin }
import ai.mantik.testutils.TestBase
import io.circe.Json

class KubernetesConverterSpec extends TestBase {

  trait Env {
    def config = Config().copy(
      sideCar = Container("my_sidecar", Seq("sidecar_arg")),
      coordinator = Container("my_coordinator", Seq("coordinator_arg")),
      payloadPreparer = Container("payload_preparer"),
      namespacePrefix = "systemtest-",
      podTrackerId = "mantik-executor",
      dockerConfig = DockerConfig(
        defaultImageTag = Some("mytag"),
        defaultImageRepository = Some("my-repo"),
        logins = Seq(
          DockerLogin("repo1", "user1", "password1")
        )
      )
    )

    lazy val converter = new KubernetesConverter(
      config,
      id = "id1",
      extraLogins = Seq(
        DockerLogin("repo2", "user2", "password2")
      ),
      superPrefix = "prefix-",
      idLabel = "idLabel"
    )
  }

  "createImagePullPolicy" should "convert nice pull policies" in new Env {
    converter.createImagePullPolicy(Container("foo")) shouldBe skuber.Container.PullPolicy.Always
    converter.createImagePullPolicy(Container("foo:latest")) shouldBe skuber.Container.PullPolicy.Always
    converter.createImagePullPolicy(Container("foo:master")) shouldBe skuber.Container.PullPolicy.IfNotPresent
    converter.createImagePullPolicy(Container("foo:other")) shouldBe skuber.Container.PullPolicy.IfNotPresent
  }

  it should "disable pulling if requested" in new Env {
    override def config: Config = {
      super.config.copy(
        kubernetesDisablePull = true
      )
    }
    converter.createImagePullPolicy(Container("foo")) shouldBe skuber.Container.PullPolicy.Never
    converter.createImagePullPolicy(Container("foo:latest")) shouldBe skuber.Container.PullPolicy.Never
    converter.createImagePullPolicy(Container("foo:other")) shouldBe skuber.Container.PullPolicy.Never
  }

  "convertNode" should "like data containers" in new Env {
    val nodeService =
      ContainerService(
        main = Container(
          image = "repo/runner:latest"
        ),
        dataProvider = Some(DataProvider(
          url = Some("url1"),
          mantikfile = Some("mf1"),
          directory = Some("dir1")
        ))
      )

    val converted = converter.convertNode("A", nodeService)
    val spec = converted.spec.get
    spec.containers.size shouldBe 2 // sidecar, main
    spec.containers.map(_.image) should contain theSameElementsAs Seq("repo/runner:latest", config.sideCar.image)
    spec.containers.find(_.name == "main").get.volumeMounts.map(_.name) shouldBe List("data")
    spec.initContainers.size shouldBe 1
    spec.initContainers.head.image shouldBe "payload_preparer"
    spec.initContainers.head.args shouldBe List("-url", "url1", "-mantikfile", "bWYx", "-pdir", "dir1")
    spec.initContainers.head.volumeMounts.map(_.name) shouldBe List("data")
    spec.volumes.map(_.name) shouldBe List("data")
    spec.containers.foreach(_.imagePullPolicy shouldBe skuber.Container.PullPolicy.Always)

    withClue("there must be an fsGroup element") {
      spec.securityContext.flatMap(_.fsGroup) shouldBe Some(1000)
    }
  }

  it should "resolve the container image" in new Env {
    val nodeService = ContainerService(
      main = Container(
        image = "runner"
      )
    )
    val converted = converter.convertNode("A", nodeService)
    val mainContainer = converted.spec.get.containers.ensuring(_.size == 2).find(_.name == "main").get
    mainContainer.image shouldBe "my-repo/runner:mytag"
  }

  "pullSecrets" should "convert pull screts" in new Env {
    val secrets = converter.pullSecret.get
    secrets.name shouldBe "prefix-id1-pullsecret"
    secrets.metadata.labels.get(converter.idLabel) shouldBe Some("id1")
    val value = secrets.data.ensuring(_.size == 1).get(".dockerconfigjson").get
    val parsed = io.circe.parser.parse(new String(value, StandardCharsets.UTF_8)).right.get
    parsed shouldBe Json.obj(
      "auths" -> Json.obj(
        "repo1" -> Json.obj(
          "username" -> Json.fromString("user1"),
          "password" -> Json.fromString("password1")
        ),
        "repo2" -> Json.obj(
          "username" -> Json.fromString("user2"),
          "password" -> Json.fromString("password2")
        )
      )
    )
  }

}

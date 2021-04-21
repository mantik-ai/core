package ai.mantik.executor.kubernetes

import java.nio.charset.StandardCharsets

import ai.mantik.executor.model.docker.{Container, DockerConfig, DockerLogin}
import ai.mantik.testutils.TestBase
import io.circe.Json

class KubernetesConverterSpec extends TestBase {

  "createImagePullPolicy" should "convert nice pull policies" in {
    KubernetesConverter.createImagePullPolicy(false, Container("foo")) shouldBe skuber.Container.PullPolicy.Always
    KubernetesConverter.createImagePullPolicy(
      false,
      Container("foo:latest")
    ) shouldBe skuber.Container.PullPolicy.Always
    KubernetesConverter.createImagePullPolicy(
      false,
      Container("foo:master")
    ) shouldBe skuber.Container.PullPolicy.IfNotPresent
    KubernetesConverter.createImagePullPolicy(
      false,
      Container("foo:other")
    ) shouldBe skuber.Container.PullPolicy.IfNotPresent
  }

  it should "disable pulling if requested" in {
    KubernetesConverter.createImagePullPolicy(true, Container("foo")) shouldBe skuber.Container.PullPolicy.Never
    KubernetesConverter.createImagePullPolicy(true, Container("foo:latest")) shouldBe skuber.Container.PullPolicy.Never
    KubernetesConverter.createImagePullPolicy(true, Container("foo:other")) shouldBe skuber.Container.PullPolicy.Never
  }

  "pullSecrets" should "convert pull screts" in {
    val oldConfig = Config.fromTypesafeConfig(typesafeConfig)
    val config = oldConfig.copy(
      common = oldConfig.common.copy(
        dockerConfig = DockerConfig(
          defaultImageTag = Some("mytag"),
          defaultImageRepository = Some("my-repo"),
          logins = Seq(
            DockerLogin("repo1", "user1", "password1")
          )
        )
      ),
      kubernetes = oldConfig.kubernetes.copy(
        namespacePrefix = "systemtest-"
      )
    )
    val secrets = KubernetesConverter
      .pullSecret(
        config,
        extraLogins = Seq(
          DockerLogin("repo2", "user2", "password2")
        )
      )
      .get

    val value = secrets.data.ensuring(_.size == 1)(".dockerconfigjson")
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

package ai.mantik.executor.model.docker

import ai.mantik.testutils.TestBase

class DockerConfigSpec extends TestBase {

  "resolveImage" should "work" in {
    def resolveImage(image: String, imageRepo: Option[String], imageTag: Option[String]): String = {
      DockerConfig(defaultImageTag = imageTag, defaultImageRepository = imageRepo, Nil).resolveImageName(image)
    }

    resolveImage("foo", Some("repo"), Some("tag")) shouldBe "repo/foo:tag"
    resolveImage("foo", None, Some("tag")) shouldBe "foo:tag"
    resolveImage("foo", Some("repo"), None) shouldBe "repo/foo"
    resolveImage("otherrepo/foo", Some("repo"), Some("tag")) shouldBe "otherrepo/foo:tag"
    resolveImage("foo:othertag", Some("repo"), Some("tag")) shouldBe "repo/foo:othertag"
    resolveImage("otherrepo/foo:othertag", Some("repo"), Some("tag")) shouldBe "otherrepo/foo:othertag"
    resolveImage("foo", None, None) shouldBe "foo"
  }
}

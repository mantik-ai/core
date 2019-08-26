package ai.mantik.executor.model.docker

import ai.mantik.testutils.TestBase

class ContainerSpec extends TestBase {

  "tag" should "work" in {
    Container("foo").imageTag shouldBe None
    Container("foo:mytag").imageTag shouldBe Some("mytag")
    Container("url/foo:mytag").imageTag shouldBe Some("mytag")
    Container("url:3000/foo:mytag").imageTag shouldBe Some("mytag")
  }

  "splitImageRepoTag" should "work" in {
    Container.splitImageRepoTag("foo:bar") shouldBe Some("foo" -> "bar")
    Container.splitImageRepoTag("foo") shouldBe None
    Container.splitImageRepoTag("url:3000/foo") shouldBe None
    Container.splitImageRepoTag("url:3000/foo:mytag") shouldBe Some("url:3000/foo" -> "mytag")
  }
}

package ai.mantik.executor.model.docker

import ai.mantik.testutils.TestBase

class ContainerSpec extends TestBase {

  "tag" should "work" in {
    Container("foo").imageTag shouldBe None
    Container("foo:mytag").imageTag shouldBe Some("mytag")
    Container("url/foo:mytag").imageTag shouldBe Some("mytag")
    Container("url:3000/foo:mytag").imageTag shouldBe Some("mytag")
  }
}

package ai.mantik.executor.docker.api

import ai.mantik.testutils.TestBase

class DockerApiHelperSpec extends TestBase {

  "decodeStatusCodeFromStatus" should "work" in {
    DockerApiHelper.decodeStatusCodeFromStatus("exited (0)") shouldBe Some(0)
    DockerApiHelper.decodeStatusCodeFromStatus("Up 4 Hours") shouldBe None
    DockerApiHelper.decodeStatusCodeFromStatus("Exited (0) 9 days ago") shouldBe Some(0)
    DockerApiHelper.decodeStatusCodeFromStatus("Exited (255) 4 hours ago") shouldBe Some(255)
  }
}

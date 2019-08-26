package ai.mantik.executor.docker

import ai.mantik.testutils.TestBase

class DockerServiceSpec extends TestBase {

  "formatInternalAddress" should "work" in {
    DockerService.formatInternalAddress(
      "foo1", 1234
    ) shouldBe "foo1.internal:1234"
  }

  "detectInternalService" should "work" in {
    DockerService.detectInternalService(
      "foo1.internal:1234"
    ) shouldBe Some("foo1" -> "foo1.internal")

    DockerService.detectInternalService(
      "http://foo1.internal:1234"
    ) shouldBe Some("foo1" -> "foo1.internal")

    DockerService.detectInternalService(
      "http://bla:1234/foo1.internal"
    ) shouldBe None

    DockerService.detectInternalService(
      "http://very.long.name.internal:4000/boom/bar"
    ) shouldBe Some(
        "very.long.name" -> "very.long.name.internal"
      )
  }
}

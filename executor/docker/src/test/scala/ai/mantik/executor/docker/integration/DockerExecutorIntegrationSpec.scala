package ai.mantik.executor.docker.integration

import ai.mantik.executor.Errors
import ai.mantik.executor.docker.{ DockerExecutor, DockerExecutorConfig }
import ai.mantik.executor.model.PublishServiceRequest
import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class DockerExecutorIntegrationSpec extends IntegrationTestBase {

  trait Env {
    val config = DockerExecutorConfig.fromTypesafeConfig(typesafeConfig)
    val dockerExecutor = new DockerExecutor(dockerClient, config)
  }

  "publishService" should "be a dummy" in new Env {
    val namedService = PublishServiceRequest(
      isolationSpace = "some_isolation",
      serviceName = "service1",
      port = 4000,
      externalName = "servicename",
      externalPort = 4000
    )
    val result = await(dockerExecutor.publishService(namedService))
    result.name shouldBe "servicename:4000"
  }

  it should "work with ip addresses" in new Env {
    val ipService = PublishServiceRequest(
      isolationSpace = "some_isolation",
      serviceName = "service1",
      port = 4000,
      externalName = "192.168.1.1",
      externalPort = 4000
    )
    val result = await(dockerExecutor.publishService(ipService))
    result.name shouldBe "192.168.1.1:4000"
  }

  it should "fail on different ports inside and outside" in new Env {
    val invalid = PublishServiceRequest(
      isolationSpace = "some_isolation",
      serviceName = "service1",
      port = 4000,
      externalName = "ignored",
      externalPort = 4001
    )
    intercept[Errors.BadRequestException] {
      await(dockerExecutor.publishService(invalid))
    }
  }

}

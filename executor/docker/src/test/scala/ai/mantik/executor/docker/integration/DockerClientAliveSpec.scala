package ai.mantik.executor.docker.integration

import ai.mantik.executor.docker.DockerConstants
import ai.mantik.executor.docker.api.DockerClient
import ai.mantik.executor.docker.api.structures.{ CreateContainerRequest, CreateVolumeRequest }
import ai.mantik.testutils.tags.IntegrationTest
import io.circe.syntax._

@IntegrationTest
class DockerClientAliveSpec extends IntegrationTestBase {

  trait Env {
  }

  it should "figure out it's version" in new Env {
    val version = await(dockerClient.version(()))
    version.ApiVersion shouldNot be(empty)
    version.Arch shouldNot be(empty)
    version.KernelVersion shouldNot be(empty)
    version.Version shouldNot be(empty)
  }

  it should "list containers" in new Env {
    val containers = await(dockerClient.listContainers(true))
    logger.info(s"Containers ${containers.asJson}")
  }

  it should "inspect a container if its living" in new Env {
    val containers = await(dockerClient.listContainers(false))
    if (containers.nonEmpty) {
      val first = containers.head
      val info = await(dockerClient.inspectContainer(first.Id))
      logger.info(s"Container ${info.asJson}")
    }
  }

  val helloWorld = "hello-world"

  it should "start / stop a simple container" in new Env {
    await(dockerClient.pullImage(helloWorld))
    val container = await(dockerClient.createContainer("mantik-systemtest-helloworld", CreateContainerRequest(
      Image = helloWorld,
      Labels = Map(
        DockerConstants.ManagedByLabelName -> DockerConstants.ManabedByLabelValue
      )
    )))
    await(dockerClient.startContainer(container.Id))
    eventually {
      await(dockerClient.containerLogs(container.Id, true, true)).toLowerCase should include("hello")
    }
    await(dockerClient.removeContainer(container.Id, true))
  }

  it should "pull and delete images" in new Env {
    pending // disabled due side effects
    await(dockerClient.pullImage(helloWorld)) // ignore result

    val inspect = await(dockerClient.inspectImage(helloWorld))
    inspect.Id shouldNot be(empty)

    await(dockerClient.removeImage(helloWorld))
    intercept[DockerClient.WrappedErrorResponse] {
      await(dockerClient.inspectImage(helloWorld))
    }.code shouldBe 404
  }

  it should "list, add and remove volumes" in new Env {
    val testVolume = "mantik-systemtest-volume1"
    val response = await(dockerClient.createVolume(
      CreateVolumeRequest(
        Name = testVolume,
        Labels = Map(
          DockerConstants.ManagedByLabelName -> DockerConstants.ManabedByLabelValue
        )
      )
    ))
    response.Name shouldBe testVolume

    val listThem = await(dockerClient.listVolumes(()))
    listThem.Volumes.find(_.Name == testVolume) shouldNot be(empty)

    await(dockerClient.removeVolume(testVolume))
    val listThemAgain = await(dockerClient.listVolumes(()))
    listThemAgain.Volumes.find(_.Name == testVolume) shouldBe empty
  }
}

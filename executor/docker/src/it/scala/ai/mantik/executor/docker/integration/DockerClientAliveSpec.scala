package ai.mantik.executor.docker.integration

import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.docker.DockerConstants
import ai.mantik.executor.docker.api.DockerClient
import ai.mantik.executor.docker.api.structures.{CreateContainerNetworkSpecificConfig, CreateContainerNetworkingConfig, CreateContainerRequest, CreateNetworkRequest, CreateVolumeRequest, ListNetworkRequestFilter}
import io.circe.syntax._

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
    val (_, source) = await(dockerClient.pullImage(helloWorld))
    collectByteSource(source)

    val container = await(dockerClient.createContainer("mantik-systemtest-helloworld", CreateContainerRequest(
      Image = helloWorld,
      Labels = Map(
        LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
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
          LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
        )
      )
    ))
    response.Name shouldBe testVolume

    val listThem = await(dockerClient.listVolumes(()))
    listThem.Volumes.find(_.Name == testVolume) shouldNot be(empty)

    val inspectIt = await(dockerClient.inspectVolume(response.Name))
    inspectIt.Name shouldBe response.Name
    inspectIt.Driver shouldNot be(empty)

    await(dockerClient.removeVolume(testVolume))
    val listThemAgain = await(dockerClient.listVolumes(()))
    listThemAgain.Volumes.find(_.Name == testVolume) shouldBe empty
  }

  it should "list, add and remove networks" in new Env {
    val testNetwork = "mantik-test-network"
    val response = await(dockerClient.createNetwork(
      CreateNetworkRequest(
        Name = testNetwork,
        Labels = Map(
          LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
        )
      )
    ))
    response.Id shouldNot be(empty)

    val listThem = await(dockerClient.listNetworks(()))
    val element = listThem.find(_.Name == testNetwork).get
    element.Name shouldBe testNetwork
    element.Id shouldBe response.Id
    element.labels(LabelConstants.ManagedByLabelName) shouldBe LabelConstants.ManagedByLabelValue

    val (_, source) = await(dockerClient.pullImage(helloWorld))
    collectByteSource(source)

    val container = await(dockerClient.createContainer("mantik-systemtest-helloworld-network", CreateContainerRequest(
      Image = helloWorld,
      Labels = Map(
        LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
      ),
      NetworkingConfig = CreateContainerNetworkingConfig(
        Map(
          testNetwork -> CreateContainerNetworkSpecificConfig(
            NetworkID = Some(response.Id)
          )
        )
      )
    )))

    val containerBack = await(dockerClient.inspectContainer(container.Id))
    containerBack.NetworkSettings.Networks(testNetwork).NetworkID shouldBe response.Id

    val listThemDifferentFilter = await(dockerClient.listNetworksFiltered(
      ListNetworkRequestFilter.forLabels(LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue)
    ))
    listThemDifferentFilter.find(_.Id == response.Id) shouldBe defined

    val listThemDifferentFilter2 = await(dockerClient.listNetworksFiltered(
      ListNetworkRequestFilter.forLabels(LabelConstants.ManagedByLabelName -> "notexisting")
    ))
    listThemDifferentFilter2.find(_.Id == response.Id) shouldBe empty

    val listThemDifferentFilter3 = await(dockerClient.listNetworksFiltered(
      ListNetworkRequestFilter(name = Some(Vector("notexisting")))
    ))
    listThemDifferentFilter3 shouldBe empty

    val listThemDifferentFilter4 = await(dockerClient.listNetworksFiltered(
      ListNetworkRequestFilter(name = Some(Vector(testNetwork)))
    ))
    listThemDifferentFilter4.size shouldBe 1


    val inspectIt = await(dockerClient.inspectNetwork(response.Id))
    inspectIt.Id shouldBe response.Id
    inspectIt.Name shouldBe testNetwork
    inspectIt.labels(LabelConstants.ManagedByLabelName) shouldBe LabelConstants.ManagedByLabelValue

    await(dockerClient.removeNetwork(response.Id))

    val listThem2 = await(dockerClient.listNetworks(()))
    listThem2.find(_.Name == testNetwork) shouldBe empty
  }
}

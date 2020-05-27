package ai.mantik.executor.docker.integration

import ai.mantik.executor.Errors
import ai.mantik.executor.docker.api.structures.ListContainerRequestFilter
import ai.mantik.executor.docker.{DockerConstants, DockerExecutor, DockerExecutorConfig}
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ListWorkerRequest, PublishServiceRequest, StartWorkerRequest, StartWorkerResponse, StopWorkerRequest, WorkerState}
import io.circe.Json

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

  trait EnvForWorkers extends Env {
    def startWorker(id: String, isolationSpace: String): StartWorkerResponse = {
      val startWorkerRequest = StartWorkerRequest (
        isolationSpace = isolationSpace,
        id = id,
        container = Container(
          image = "mantikai/bridge.binary"
        )
      )
      await(dockerExecutor.startWorker(startWorkerRequest))
    }
  }

  "startWorker" should "should work" in new EnvForWorkers {
    val response = startWorker("foo", "some_isolation")
    response.nodeName shouldNot be(empty)
    val containers = await(dockerClient.listContainers(true))
    val container = containers.find(_.Names.contains("/" + response.nodeName)).getOrElse(fail)
    container.Labels(DockerConstants.IsolationSpaceLabelName) shouldBe "some_isolation"
    container.Labels(DockerConstants.UserIdLabelName) shouldBe "foo"
    container.Labels(DockerConstants.ManagedByLabelName) shouldBe DockerConstants.ManagedByLabelValue
    container.Labels(DockerConstants.TypeLabelName) shouldBe DockerConstants.WorkerType

    eventually {
      val containerAgain = await(dockerClient.listContainersFiltered(true, ListContainerRequestFilter.forLabelKeyValue(
        DockerConstants.UserIdLabelName -> "foo"
      )))
      import io.circe.syntax._
      println(containerAgain.asJson)
      containerAgain.head.State shouldBe "running"
    }
  }

  "listWorkers" should "work" in new EnvForWorkers {
    val response = await(dockerExecutor.listWorkers(ListWorkerRequest("other_isolation")))
    response.workers shouldBe empty

    val startResponse1 = startWorker("x1", "other_isolation")
    val startResponse2 = startWorker("x2", "other_isolation")

    val response2 = await(dockerExecutor.listWorkers(ListWorkerRequest("other_isolation")))
    response2.workers.size shouldBe 2
    response2.workers.map(_.id) should contain theSameElementsAs Seq("x1", "x2")

    // id filter
    val response3 = await(dockerExecutor.listWorkers(ListWorkerRequest("other_isolation", idFilter = Some("x1"))))
    response3.workers.size shouldBe 1
    response3.workers.head.id shouldBe "x1"

    // name filter
    val response4 = await(dockerExecutor.listWorkers(ListWorkerRequest("other_isolation", nameFilter = Some(startResponse2.nodeName))))
    response4.workers.size shouldBe 1
    response4.workers.head.id shouldBe "x2"

    // isolationSpace
    val response5 = await(dockerExecutor.listWorkers(ListWorkerRequest("not_existing")))
    response5.workers shouldBe empty
  }

  "stopWorkers" should "work" in new EnvForWorkers {
    val container1 = startWorker("x1", "stop_test")
    val container2 = startWorker("x2", "stop_test")
    val container3 = startWorker("x3", "stop_test")
    val container4 = startWorker("x4", "stop_test2")

    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test", idFilter = Some("x1"))))
      listResponse.workers.head.state shouldBe WorkerState.Running
    }

    // by id
    await(dockerExecutor.stopWorker(StopWorkerRequest("stop_test", idFilter = Some("x1"))))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test", idFilter = Some("x1"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }

    // by name
    await(dockerExecutor.stopWorker(StopWorkerRequest("stop_test", nameFilter = Some(container2.nodeName))))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test", idFilter = Some("x2"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }

    // by all
    await(dockerExecutor.stopWorker(StopWorkerRequest("stop_test")))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test", idFilter = Some("x3"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }

    // however the other isolation space should be not infected
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test2")))
      listResponse.workers.head.state shouldBe WorkerState.Running
    }

  }
}

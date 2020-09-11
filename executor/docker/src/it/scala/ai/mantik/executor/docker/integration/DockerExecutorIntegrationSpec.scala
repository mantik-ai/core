package ai.mantik.executor.docker.integration

import java.util.Base64

import ai.mantik.executor.Errors
import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.docker.api.structures.ListContainerRequestFilter
import ai.mantik.executor.docker.{DockerConstants, DockerExecutor, DockerExecutorConfig}
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ListWorkerRequest, MnpWorkerDefinition, PublishServiceRequest, StartWorkerRequest, StartWorkerResponse, StopWorkerRequest, WorkerState, WorkerType}
import akka.util.ByteString
import io.circe.syntax._

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
        definition = MnpWorkerDefinition(
          container = Container(
            image = "mantikai/bridge.binary"
          )
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
    container.Labels(LabelConstants.UserIdLabelName) shouldBe "foo"
    container.Labels(LabelConstants.ManagedByLabelName) shouldBe LabelConstants.ManagedByLabelValue
    container.Labels(LabelConstants.WorkerTypeLabelName) shouldBe LabelConstants.workerType.mnpWorker

    eventually {
      val containerAgain = await(dockerClient.listContainersFiltered(true, ListContainerRequestFilter.forLabelKeyValue(
        LabelConstants.UserIdLabelName -> "foo"
      )))
      println(containerAgain.asJson)
      containerAgain.head.State shouldBe "running"
    }
  }

  it should "be possible to initialize an MNP Node directly" in new Env {
    // Source: SelectSpec, just dumping it out as Base64
    val serializedInitRequest =
      "CiY0YjU0NTk3MS0yMmY4LTRiMjItYWE4Ni1jNmY2NDk4YTI0NWVfMhLRCApDdHlwZS5nb29nbGVhcGlz" +
      "LmNvbS9haS5tYW50aWsuYnJpZGdlLnByb3Rvcy5NYW50aWtJbml0Q29uZmlndXJhdGlvbhKJCAqGCHsK" +
      "ICAia2luZCIgOiAiYWxnb3JpdGhtIiwKICAiYnJpZGdlIiA6ICJidWlsdGluL3NlbGVjdCIsCiAgInR5" +
      "cGUiIDogewogICAgImlucHV0IiA6IHsKICAgICAgInR5cGUiIDogInRhYnVsYXIiLAogICAgICAiY29s" +
      "dW1ucyIgOiB7CiAgICAgICAgIngiIDogImludDMyIiwKICAgICAgICAieSIgOiAic3RyaW5nIgogICAg" +
      "ICB9CiAgICB9LAogICAgIm91dHB1dCIgOiB7CiAgICAgICJ0eXBlIiA6ICJ0YWJ1bGFyIiwKICAgICAg" +
      "ImNvbHVtbnMiIDogewogICAgICAgICJhIiA6ICJpbnQzMiIsCiAgICAgICAgInkiIDogInN0cmluZyIK" +
      "ICAgICAgfQogICAgfQogIH0sCiAgInNlbGVjdFByb2dyYW0iIDogewogICAgInNlbGVjdG9yIiA6IHsK" +
      "ICAgICAgImFyZ3MiIDogMSwKICAgICAgInJldFN0YWNrRGVwdGgiIDogMSwKICAgICAgInN0YWNrSW5p" +
      "dERlcHRoIiA6IDIsCiAgICAgICJvcHMiIDogWwogICAgICAgICJnZXQiLAogICAgICAgIDAsCiAgICAg" +
      "ICAgImNudCIsCiAgICAgICAgewogICAgICAgICAgInR5cGUiIDogImludDgiLAogICAgICAgICAgInZh" +
      "bHVlIiA6IDIKICAgICAgICB9LAogICAgICAgICJjYXN0IiwKICAgICAgICAiaW50OCIsCiAgICAgICAg" +
      "ImludDMyIiwKICAgICAgICAiZXEiLAogICAgICAgICJpbnQzMiIsCiAgICAgICAgIm5lZyIKICAgICAg" +
      "XQogICAgfSwKICAgICJwcm9qZWN0b3IiIDogewogICAgICAiYXJncyIgOiAyLAogICAgICAicmV0U3Rh" +
      "Y2tEZXB0aCIgOiAyLAogICAgICAic3RhY2tJbml0RGVwdGgiIDogMiwKICAgICAgIm9wcyIgOiBbCiAg" +
      "ICAgICAgImdldCIsCiAgICAgICAgMCwKICAgICAgICAiY250IiwKICAgICAgICB7CiAgICAgICAgICAi" +
      "dHlwZSIgOiAiaW50OCIsCiAgICAgICAgICAidmFsdWUiIDogMQogICAgICAgIH0sCiAgICAgICAgImNh" +
      "c3QiLAogICAgICAgICJpbnQ4IiwKICAgICAgICAiaW50MzIiLAogICAgICAgICJibiIsCiAgICAgICAg" +
      "ImludDMyIiwKICAgICAgICAiYWRkIiwKICAgICAgICAiZ2V0IiwKICAgICAgICAxCiAgICAgIF0KICAg" +
      "IH0KICB9Cn0aHQobYXBwbGljYXRpb24veC1tYW50aWstYnVuZGxlIh0KG2FwcGxpY2F0aW9uL3gtbWFu" +
      "dGlrLWJ1bmRsZQ=="
    val initRequest = ByteString(Base64.getDecoder.decode(serializedInitRequest))
    val startWorkerRequest = StartWorkerRequest (
      isolationSpace = "start_with_init",
      id = "startme",
      definition = MnpWorkerDefinition(
        container = Container(
          image = "mantikai/bridge.select"
        ),
        initializer = Some(initRequest)
      )
    )
    val response = await(dockerExecutor.startWorker(startWorkerRequest))
    response.nodeName shouldNot be(empty)
    val containers = await(dockerClient.listContainers(true))
    containers.find(_.Names.contains(s"/${response.nodeName}")) shouldBe defined
    val initContainer = containers.find(_.Names.contains(s"/${response.nodeName}_init"))
    initContainer shouldBe defined
    logger.info(s"InitContainer ${initContainer.get.asJson}")
    initContainer.get.State shouldBe "exited"
  }

  "listWorkers" should "work" in new EnvForWorkers {
    val response = await(dockerExecutor.listWorkers(ListWorkerRequest("other_isolation")))
    response.workers shouldBe empty

    val startResponse1 = startWorker("x1", "other_isolation")
    val startResponse2 = startWorker("x2", "other_isolation")

    val response2 = await(dockerExecutor.listWorkers(ListWorkerRequest("other_isolation")))
    response2.workers.size shouldBe 2
    response2.workers.map(_.id) should contain theSameElementsAs Seq("x1", "x2")
    response2.workers.map(_.`type`) should contain theSameElementsAs Seq(WorkerType.MnpWorker, WorkerType.MnpWorker)

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
    await(dockerExecutor.stopWorker(StopWorkerRequest("stop_test", idFilter = Some("x1"), remove = false)))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test", idFilter = Some("x1"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }

    // by name
    await(dockerExecutor.stopWorker(StopWorkerRequest("stop_test", nameFilter = Some(container2.nodeName), remove = false)))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test", idFilter = Some("x2"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }

    // by all
    await(dockerExecutor.stopWorker(StopWorkerRequest("stop_test", remove = false)))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test", idFilter = Some("x3"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }

    // however the other isolation space should be not infected
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test2")))
      listResponse.workers.head.state shouldBe WorkerState.Running
    }

    // removing all
    await(dockerExecutor.stopWorker(StopWorkerRequest("stop_test", remove = true)))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest("stop_test")))
      listResponse.workers shouldBe empty
    }
  }
}

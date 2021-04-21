package ai.mantik.executor.common.test.integration

import java.util.Base64

import ai.mantik.executor.Executor
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ListWorkerRequest, MnpPipelineDefinition, MnpWorkerDefinition, StartWorkerRequest, StartWorkerResponse, StopWorkerRequest, WorkerState, WorkerType }
import ai.mantik.testutils.TestBase
import akka.util.ByteString

trait StartWorkerSpecBase {
  self: IntegrationBase with TestBase =>

  val isolationSpace = "start-worker-test"
  // user Id given to all containers
  val userId = "userId"

  protected def checkEmptyNow(): Unit = {
    // Addional check for implementors
  }

  protected def checkExistance(executor: Executor, startWorkerResponse: StartWorkerResponse, expectedType: WorkerType): Unit = {
    val listResponse = await(executor.listWorkers(ListWorkerRequest(
      isolationSpace
    )))

    val element = listResponse.workers.find(_.nodeName == startWorkerResponse.nodeName)
    element shouldBe defined

    element.get.`type` shouldBe expectedType
    element.get.externalUrl shouldBe startWorkerResponse.externalUrl
  }

  protected def stopAndKill(executor: Executor, startWorkerResponse: StartWorkerResponse): Unit = {
    val stopResponse = await(executor.stopWorker(StopWorkerRequest(
      isolationSpace,
      nameFilter = Some(startWorkerResponse.nodeName),
      remove = false
    )))
    stopResponse.removed shouldNot be(empty)

    eventually {
      val listResponse2 = await(executor.listWorkers(ListWorkerRequest(isolationSpace)))
      listResponse2.workers.find(_.nodeName == startWorkerResponse.nodeName).get.state shouldBe 'terminal
    }

    val stopResponse2 = await(executor.stopWorker(StopWorkerRequest(
      isolationSpace,
      nameFilter = Some(startWorkerResponse.nodeName),
      remove = true
    )))
    stopResponse2.removed shouldNot be(empty)

    eventually {
      val listResponse2 = await(executor.listWorkers(ListWorkerRequest(isolationSpace)))
      listResponse2.workers.find(_.nodeName == startWorkerResponse.nodeName) shouldBe empty
    }

    checkEmptyNow()
  }

  private val simpleStartWorker = StartWorkerRequest(
    isolationSpace = isolationSpace,
    id = userId,
    definition = MnpWorkerDefinition(
      container = Container(
        image = "mantikai/bridge.binary"
      )
    )
  )

  private val pipelineRequest = StartWorkerRequest(
    id = userId,
    isolationSpace = isolationSpace,
    definition = MnpPipelineDefinition(
      io.circe.parser.parse(
        """{
          |  "name": "my_pipeline",
          |  "steps": [],
          |  "inputType": "int32"
          |}
          |""".stripMargin).forceRight
    )
  )

  it should "allow running a simple worker" in withExecutor { executor =>
    val response = await(executor.startWorker(simpleStartWorker))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe empty

    checkExistance(executor, response, WorkerType.MnpWorker)

    stopAndKill(executor, response)
  }

  it should "allow running a simple worker with name hint" in withExecutor { executor =>
    val nameHint = "name1"
    val response = await(executor.startWorker(simpleStartWorker.copy(nameHint = Some(nameHint))))
    response.nodeName shouldNot be(empty)
    response.nodeName should include(nameHint)

    checkExistance(executor, response, WorkerType.MnpWorker)

    stopAndKill(executor, response)
  }

  it should "allow deploying a persistent worker with initializer" in withExecutor { executor =>
    val startWorkerRequest = StartWorkerRequest(
      isolationSpace = isolationSpace,
      id = userId,
      definition = MnpWorkerDefinition(
        container = Container(
          image = "mantikai/bridge.select"
        ),
        initializer = Some(TestData.selectInitRequest)
      ),
      keepRunning = true
    )

    val response = await(executor.startWorker(startWorkerRequest))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe empty

    checkExistance(executor, response, WorkerType.MnpWorker)

    stopAndKill(executor, response)
  }

  it should "allow deploying a pipeline" in withExecutor { executor =>
    val response = await(executor.startWorker(pipelineRequest))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe empty

    checkExistance(executor, response, WorkerType.MnpPipeline)

    stopAndKill(executor, response)
  }

  it should "allow deploying a persistent pipeline" in withExecutor { executor =>
    val response = await(executor.startWorker(pipelineRequest.copy(
      keepRunning = true
    )))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe empty

    checkExistance(executor, response, WorkerType.MnpPipeline)

    stopAndKill(executor, response)
  }

  it should "allow deplying a persistent pipeline with ingress" in withExecutor { executor =>
    val response = await(executor.startWorker(pipelineRequest.copy(
      ingressName = Some("pipe1")
    )))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe defined

    checkExistance(executor, response, WorkerType.MnpPipeline)

    stopAndKill(executor, response)
  }
}
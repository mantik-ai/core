package ai.mantik.executor.common.test.integration

import ai.mantik.executor.model.{ ListWorkerRequest, MnpPipelineDefinition, StartWorkerRequest, WorkerType }
import ai.mantik.testutils.{ HttpSupport, TestBase }
import akka.util.ByteString

trait DeployPipelineSpecBase {
  self: IntegrationBase with TestBase with HttpSupport =>

  val pipeDef =
    """
      |{
      |  "name": "my_pipeline",
      |  "steps": [],
      |  "inputType": "int32"
      |}
    """.stripMargin

  it should "allow deploying a pipeline" in withExecutor { executor =>
    // we deploy an empty pipeline here, as this tests do not have a docker container with real
    // bridge images here, only fake ones.
    // and the pipelines checks types.
    val isolationSpace = "deploy-pipe-spec"
    val parsedDef = io.circe.parser.parse(pipeDef).forceRight

    val pipelineRequest = StartWorkerRequest(
      id = "service1",
      nameHint = Some("my-service"),
      isolationSpace = isolationSpace,
      definition = MnpPipelineDefinition(
        definition = parsedDef
      ),
      ingressName = Some("ingress1")
    )

    val response = await(executor.startWorker(pipelineRequest))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldNot be(empty)

    eventually {
      val applyUrl = response.externalUrl.get + "/apply"
      val httpPostResponse = httpPost(applyUrl, "application/json", ByteString.fromString("100"))
      httpPostResponse shouldBe ByteString.fromString("100")
    }

    val listResponse = await(executor.listWorkers(ListWorkerRequest(isolationSpace)))
    val pipe = listResponse.workers.find(_.`type` == WorkerType.MnpPipeline)
    pipe shouldBe defined
    pipe.get.nodeName shouldBe response.nodeName
    pipe.get.externalUrl shouldBe response.externalUrl
  }
}

package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.model.{ DeployServiceRequest, DeployableService }
import ai.mantik.testutils.HttpSupport
import ai.mantik.testutils.tags.IntegrationTest
import akka.util.ByteString

@IntegrationTest
class DeployPipelineSpec extends IntegrationTestBase with HttpSupport {

  val pipeDef =
    """
      |{
      |  "name": "my_pipeline",
      |  "steps": [],
      |  "inputType": "int32"
      |}
    """.stripMargin

  it should "allow deploying a pipeline" in new Env {
    // we deploy an empty pipeline here, as this tests do not have a docker container with real
    // bridge images here, only fake ones.
    // and the pipelines checks types.
    val isolationSpace = "deploy-pipe-spec"
    val parsedDef = io.circe.parser.parse(pipeDef).forceRight

    val pipelineRequest = DeployServiceRequest(
      serviceId = "service1",
      nameHint = Some("my-service"),
      isolationSpace = isolationSpace,
      service = DeployableService.Pipeline(
        pipeline = parsedDef
      ),
      ingress = Some("ingress1")
    )

    val response = await(executor.deployService(pipelineRequest))
    response.url shouldNot be(empty)
    response.externalUrl shouldNot be(empty)

    eventually {
      val applyUrl = response.externalUrl.get + "/apply"
      val httpPostResponse = httpPost(applyUrl, "application/json", ByteString.fromString("100"))
      httpPostResponse shouldBe ByteString.fromString("100")
    }
  }
}

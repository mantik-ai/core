package ai.mantik.planner.pipelines

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.testutils.TestBase

import io.circe.syntax._

class PipelineRuntimeDefinitionSpec extends TestBase {

  val sample =
    """
      |{
      |  "name": "My nice pipeline",
      |  "inputType": "int32",
      |  "steps": [
      |    {
      |      "url": "http://service1",
      |      "outputType": "float32"
      |    },
      |    {
      |      "url": "http://service2",
      |      "outputType": "string"
      |    }
      |  ]
      |}
    """.stripMargin

  it should "be compatible with golangs data type" in {
    val parsed = CirceJson.forceParseJson(sample).as[PipelineRuntimeDefinition].forceRight
    parsed shouldBe PipelineRuntimeDefinition(
      name = "My nice pipeline",
      inputType = FundamentalType.Int32,
      steps = Seq(
        PipelineRuntimeDefinition.Step(
          "http://service1",
          FundamentalType.Float32
        ),
        PipelineRuntimeDefinition.Step(
          "http://service2",
          FundamentalType.StringType
        )
      )
    )

    parsed.asJson.as[PipelineRuntimeDefinition].forceRight shouldBe parsed
  }
}

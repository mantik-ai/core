package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{ AlgorithmDefinition, Mantikfile }
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.testutils.TestBase

class MantikItemSpec extends TestBase {

  lazy val sample = Algorithm(
    Source.constructed(PayloadSource.Loaded("1", ContentTypes.ZipFileContentType)),
    Mantikfile.fromYaml(
      """
        |stack: stack1
        |metaVariables:
        |  - name: x
        |    value: 123
        |    type: int32
        |type:
        |  input: int32
        |  output: float32
      """.stripMargin
    ).right.getOrElse(fail).cast[AlgorithmDefinition].getOrElse(fail)
  )

  "withMetaVariable" should "update meta variables" in {
    sample.mantikfile.metaJson.metaVariable("x").get.value shouldBe Bundle.fundamental(123)
    val after = sample.withMetaValue("x", 100)
    after.mantikfile.metaJson.metaVariable("x").get.value shouldBe Bundle.fundamental(100)
    after.payloadSource shouldBe sample.payloadSource
    after.source.definition shouldBe an[DefinitionSource.Derived]
    after.mantikfile.definition shouldBe an[AlgorithmDefinition]
  }
}

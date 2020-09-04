package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{ AlgorithmDefinition, ItemId, MantikHeader, NamedMantikId }
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.util.FakeBridges
import ai.mantik.testutils.TestBase
import io.circe.syntax._

class MantikItemSpec extends TestBase {
  import MantikItemSpec.sample

  "MantikItem" should "be JSON serializable" in {
    val asJson = (MantikItemSpec.sample: MantikItem).asJson
    val back = asJson.as[MantikItem]
    back.forceRight.asJson shouldBe asJson
    val wanted = Right(MantikItemSpec.sample)
    back shouldBe wanted
    // More Tests are in MantikItemCodecSpec
  }

  "withMetaVariable" should "update meta variables" in {
    sample.mantikHeader.metaJson.metaVariable("x").get.value shouldBe Bundle.fundamental(123)
    sample.mantikId shouldBe an[ItemId]

    val after = sample.withMetaValue("x", 100)
    after.mantikHeader.metaJson.metaVariable("x").get.value shouldBe Bundle.fundamental(100)
    after.payloadSource shouldBe sample.payloadSource
    after.source.definition shouldBe an[DefinitionSource.Derived]
    after.mantikHeader.definition shouldBe an[AlgorithmDefinition]
    after.mantikId shouldBe an[ItemId]
    after.itemId shouldNot be(sample.itemId)
  }
}

object MantikItemSpec extends FakeBridges {
  lazy val sample = Algorithm(
    Source.constructed(PayloadSource.Loaded("1", ContentTypes.ZipFileContentType)),
    MantikHeader.fromYaml(
      """
        |bridge: algo1
        |metaVariables:
        |  - name: x
        |    value: 123
        |    type: int32
        |type:
        |  input: int32
        |  output: float32
      """.stripMargin
    ).right.getOrElse(???).cast[AlgorithmDefinition].getOrElse(???),
    algoBridge
  )
}
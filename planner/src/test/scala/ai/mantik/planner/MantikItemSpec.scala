package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{ AlgorithmDefinition, ItemId, Mantikfile, NamedMantikId }
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.util.FakeBridges
import ai.mantik.testutils.TestBase

class MantikItemSpec extends TestBase with FakeBridges {

  lazy val sample = Algorithm(
    Source.constructed(PayloadSource.Loaded("1", ContentTypes.ZipFileContentType)),
    Mantikfile.fromYaml(
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
    ).right.getOrElse(fail).cast[AlgorithmDefinition].getOrElse(fail),
    algoBridge
  )

  "withMetaVariable" should "update meta variables" in {
    sample.mantikfile.metaJson.metaVariable("x").get.value shouldBe Bundle.fundamental(123)
    sample.mantikId shouldBe an[ItemId]
    sample.state.update(_.copy(namedMantikItem = Some("foo")))
    sample.mantikId shouldBe NamedMantikId(name = "foo")

    val after = sample.withMetaValue("x", 100)
    after.mantikfile.metaJson.metaVariable("x").get.value shouldBe Bundle.fundamental(100)
    after.payloadSource shouldBe sample.payloadSource
    after.source.definition shouldBe an[DefinitionSource.Derived]
    after.mantikfile.definition shouldBe an[AlgorithmDefinition]
    after.mantikId shouldBe an[ItemId]
  }

  "initialisation" should "take over values correctly for loaded items" in {
    val name = NamedMantikId("foo/bar")
    val itemId = ItemId.generate()
    val algorithm = Algorithm(
      Source(DefinitionSource.Loaded(
        Some(name),
        itemId
      ), PayloadSource.Empty),
      sample.mantikfile,
      algoBridge
    )
    algorithm.itemId shouldBe itemId
    algorithm.name shouldBe Some(name)
    val state = algorithm.state.get
    state.itemStored shouldBe true
    state.nameStored shouldBe true
    state.deployment shouldBe None
    state.namedMantikItem shouldBe Some(name)
    state.payloadFile shouldBe None
  }

  it should "also work for anonymous items" in {
    val itemId = ItemId.generate()
    val algorithm = Algorithm(
      Source(DefinitionSource.Loaded(
        None,
        itemId
      ), PayloadSource.Loaded("file1", ContentTypes.ZipFileContentType)),
      sample.mantikfile,
      algoBridge
    )
    algorithm.itemId shouldBe itemId
    algorithm.name shouldBe empty
    val state = algorithm.state.get
    state.itemStored shouldBe true
    state.nameStored shouldBe false
    state.deployment shouldBe None
    state.namedMantikItem shouldBe None
    state.payloadFile shouldBe Some("file1")
  }

  it should "also work for tagged items" in {
    val name = NamedMantikId("foo/bar")
    val otherName = NamedMantikId("new/name")
    val itemId = ItemId.generate()
    val algorithm = Algorithm(
      Source(
        DefinitionSource.Tagged(
          otherName,
          DefinitionSource.Loaded(Some(name), itemId)
        ),
        PayloadSource.Empty),
      sample.mantikfile,
      algoBridge
    )
    algorithm.itemId shouldBe itemId
    algorithm.name shouldBe Some(otherName)
    val state = algorithm.state.get
    state.itemStored shouldBe true
    state.nameStored shouldBe false
    state.deployment shouldBe None
    state.namedMantikItem shouldBe Some(otherName)
    state.payloadFile shouldBe None
  }

  it should "also work for derived items" in {
    val name = NamedMantikId("foo/bar")
    val itemId = ItemId.generate()
    val algorithm = Algorithm(
      Source(DefinitionSource.Loaded(
        Some(name),
        itemId
      ), PayloadSource.Empty).derive,
      sample.mantikfile,
      algoBridge
    )
    algorithm.itemId shouldNot be(itemId)
    algorithm.name shouldBe empty
    val state = algorithm.state.get
    state.itemStored shouldBe false
    state.nameStored shouldBe false
    state.deployment shouldBe None
    state.namedMantikItem shouldBe None
    state.payloadFile shouldBe None
  }

}

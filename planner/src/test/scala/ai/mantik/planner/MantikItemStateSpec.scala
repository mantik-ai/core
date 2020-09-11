package ai.mantik.planner

import ai.mantik.elements.{ ItemId, NamedMantikId }
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.testutils.TestBase

class MantikItemStateSpec extends TestBase {

  "initialisation" should "take over values correctly for loaded items" in {
    val name = NamedMantikId("foo/bar")
    val itemId = ItemId.generate()
    val algorithm = Algorithm(
      Source(DefinitionSource.Loaded(
        Some(name),
        itemId
      ), PayloadSource.Empty),
      MantikItemSpec.sample.mantikHeader,
      MantikItemSpec.algoBridge
    )
    algorithm.itemId shouldBe itemId

    val state = MantikItemState.initializeFromSource(algorithm.source)

    state.namedMantikItem shouldBe Some(name)
    algorithm.mantikId shouldBe name
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
      MantikItemSpec.sample.mantikHeader,
      MantikItemSpec.algoBridge
    )
    algorithm.itemId shouldBe itemId
    val state = MantikItemState.initializeFromSource(algorithm.source)
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
      MantikItemSpec.sample.mantikHeader,
      MantikItemSpec.algoBridge
    )
    val state = MantikItemState.initializeFromSource(algorithm.source)
    algorithm.itemId shouldBe itemId
    state.namedMantikItem shouldBe Some(otherName)
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
      MantikItemSpec.sample.mantikHeader,
      MantikItemSpec.algoBridge
    )
    algorithm.itemId shouldNot be(itemId)
    val state = MantikItemState.initializeFromSource(algorithm.source)
    state.namedMantikItem shouldBe empty
    state.itemStored shouldBe false
    state.nameStored shouldBe false
    state.deployment shouldBe None
    state.namedMantikItem shouldBe None
    state.payloadFile shouldBe None
  }

}

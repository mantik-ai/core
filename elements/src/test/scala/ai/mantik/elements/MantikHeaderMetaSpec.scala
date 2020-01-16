package ai.mantik.elements

import ai.mantik.testutils.TestBase

class MantikHeaderMetaSpec extends TestBase {

  it should "figure out the mantikId" in {
    MantikHeaderMeta().id shouldBe None
    MantikHeaderMeta(
      name = Some("foo")
    ).id shouldBe Some(NamedMantikId("foo"))

    MantikHeaderMeta(
      account = Some("mantik"),
      name = Some("foo")
    ).id shouldBe Some(NamedMantikId("mantik/foo"))

    val bad = MantikHeaderMeta(
      account = Some("mantik"),
      name = Some("mantik/foo")
    )
    bad.id.get.violations shouldNot be(empty)
  }
}

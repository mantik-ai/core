package ai.mantik.elements

import ai.mantik.testutils.TestBase

class MantikHeaderSpec extends TestBase {

  it should "figure out the mantikId" in {
    MantikHeader().id shouldBe None
    MantikHeader(
      name = Some("foo")
    ).id shouldBe Some(NamedMantikId("foo"))

    MantikHeader(
      account = Some("mantik"),
      name = Some("foo")
    ).id shouldBe Some(NamedMantikId("mantik/foo"))

    val bad = MantikHeader(
      account = Some("mantik"),
      name = Some("mantik/foo")
    )
    bad.id.get.violations shouldNot be(empty)
  }
}

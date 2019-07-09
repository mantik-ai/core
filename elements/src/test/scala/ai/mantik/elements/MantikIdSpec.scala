package ai.mantik.elements

import ai.mantik.testutils.TestBase

class MantikIdSpec extends TestBase {

  it should "have support for anonymous" in {
    val notAnonym = MantikId("foo", "bar")
    notAnonym.isAnonymous shouldBe false
    val anonym = ItemId.generate().asAnonymousMantikId
    anonym.isAnonymous shouldBe true
    anonym.version shouldBe MantikId.DefaultVersion
    anonym.name should startWith(MantikId.AnonymousPrefix)
    MantikId.fromString(anonym.toString) shouldBe anonym
  }
}

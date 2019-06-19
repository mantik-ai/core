package ai.mantik.repository

import ai.mantik.testutils.TestBase

class MantikIdSpec extends TestBase {

  it should "be generateable" in {
    val generated = MantikId.randomGenerated()
    generated.version shouldBe MantikId.DefaultVersion
    generated.name should startWith(MantikId.GeneratedPrefix)
    MantikId.fromString(generated.toString) shouldBe generated
  }
}

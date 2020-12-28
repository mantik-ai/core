package ai.mantik.ds.element

import ai.mantik.ds.{ FundamentalType, TypeSamples }
import ai.mantik.testutils.TestBase

class ElementOrderingSpec extends TestBase {

  it should "work for primitives" in {
    TypeSamples.fundamentalSamples.foreach {
      case (ft, sample) =>
        val ordering = ElementOrdering.elementOrdering(ft)
        withClue(s"it works for ${ft}") {
          ordering.lt(sample, sample) shouldBe false
          ordering.gteq(sample, sample) shouldBe true
        }
    }
  }
}

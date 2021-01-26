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

  it should "work for non-primitives" in {
    TypeSamples.nonFundamentals.foreach {
      case (dt, sample) =>
        val ordering = ElementOrdering.elementOrdering(dt)
        withClue(s"it works for ${dt}") {
          ordering.lt(sample, sample) shouldBe false
          ordering.gteq(sample, sample) shouldBe true
        }
    }
  }
}

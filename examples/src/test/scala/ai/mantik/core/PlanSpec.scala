package ai.mantik.core

import ai.mantik.ds.FundamentalType.Int32
import ai.mantik.ds.element.{ Bundle, Primitive, SingleElement }
import ai.mantik.testutils.TestBase

class PlanSpec extends TestBase {

  // Fake plans for testing
  val plan1 = Plan.PushBundle(Bundle(Int32, Vector(SingleElement(Primitive(1)))), "1")
  val plan2 = Plan.PushBundle(Bundle(Int32, Vector(SingleElement(Primitive(1)))), "2")
  val plan3 = Plan.PushBundle(Bundle(Int32, Vector(SingleElement(Primitive(1)))), "3")

  "combine" should "be efficient" in {
    Plan.combine(Plan.Empty, Plan.Empty) shouldBe Plan.Empty
    Plan.combine(plan1, Plan.Empty) shouldBe plan1
    Plan.combine(Plan.Empty, plan1) shouldBe plan1

    Plan.combine(Plan.seq(plan1, plan2), Plan.Empty) shouldBe Plan.seq(plan1, plan2)
    Plan.combine(Plan.Empty, Plan.seq(plan1, plan2)) shouldBe Plan.seq(plan1, plan2)

    Plan.combine(plan1, plan2) shouldBe Plan.seq(plan1, plan2)
    Plan.combine(Plan.seq(plan1, plan2), plan3) shouldBe Plan.seq(plan1, plan2, plan3)
    Plan.combine(plan1, Plan.seq(plan2, plan3)) shouldBe Plan.seq(plan1, plan2, plan3)

    Plan.combine(Plan.seq(plan1, plan2), Plan.seq(plan2, plan3)) shouldBe Plan.seq(plan1, plan2, plan2, plan3)
  }
}

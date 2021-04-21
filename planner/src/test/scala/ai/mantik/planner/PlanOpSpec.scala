package ai.mantik.planner

import ai.mantik.ds.FundamentalType.Int32
import ai.mantik.ds.element.{Bundle, Primitive, SingleElement}
import ai.mantik.testutils.TestBase

class PlanOpSpec extends TestBase {

  // Fake plans for testing
  val plan1 = PlanOp.StoreBundleToFile(Bundle(Int32, Vector(SingleElement(Primitive(1)))), PlanFileReference(1))
  val plan2 = PlanOp.StoreBundleToFile(Bundle(Int32, Vector(SingleElement(Primitive(1)))), PlanFileReference(2))
  val plan3 = PlanOp.StoreBundleToFile(Bundle(Int32, Vector(SingleElement(Primitive(1)))), PlanFileReference(3))
  val plan4 = PlanOp.LoadBundleFromFile(Int32, PlanFileReference(123))

  "combine" should "be efficient" in {
    PlanOp.combine(PlanOp.Empty, PlanOp.Empty) shouldBe PlanOp.Empty
    PlanOp.combine(plan1, PlanOp.Empty) shouldBe plan1
    PlanOp.combine(plan4, PlanOp.Empty) shouldBe PlanOp.seq(plan4, PlanOp.Empty) // plan4 has type
    PlanOp.combine(PlanOp.Empty, plan1) shouldBe plan1

    PlanOp.combine(PlanOp.seq(plan1, plan2), PlanOp.Empty) shouldBe PlanOp.seq(plan1, plan2, PlanOp.Empty)
    PlanOp.combine(PlanOp.Empty, PlanOp.seq(plan1, plan2)) shouldBe PlanOp.seq(plan1, plan2)

    PlanOp.combine(plan1, plan2) shouldBe PlanOp.seq(plan1, plan2)
    PlanOp.combine(PlanOp.seq(plan1, plan2), plan3) shouldBe PlanOp.seq(plan1, plan2, plan3)
    PlanOp.combine(plan1, PlanOp.seq(plan2, plan3)) shouldBe PlanOp.seq(plan1, plan2, plan3)

    PlanOp.combine(PlanOp.seq(plan1, plan2), PlanOp.seq(plan2, plan3)) shouldBe PlanOp.seq(plan1, plan2, plan2, plan3)
  }

  "compress" should "work" in {
    PlanOp.compress(PlanOp.Empty) shouldBe PlanOp.Empty
    PlanOp.compress(PlanOp.seq()) shouldBe PlanOp.Empty
    PlanOp.compress(PlanOp.seq(plan1)) shouldBe plan1
    PlanOp.compress(PlanOp.seq(plan1, plan2)) shouldBe PlanOp.seq(plan1, plan2)
    PlanOp.compress(PlanOp.seq(plan1, PlanOp.seq(plan2, plan3))) shouldBe PlanOp.seq(plan1, plan2, plan3)
    PlanOp.compress(PlanOp.seq(PlanOp.seq(plan1, plan2), plan3)) shouldBe PlanOp.seq(plan1, plan2, plan3)

    PlanOp.compress(
      PlanOp.Sequential(Seq(PlanOp.Empty, plan1), plan2)
    ) shouldBe PlanOp.seq(plan1, plan2)
  }
}

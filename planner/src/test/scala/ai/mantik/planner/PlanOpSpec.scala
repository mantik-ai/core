/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
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

  "flatWithCoordinates" should "work" in {
    PlanOp.flatWithCoordinates(PlanOp.Empty) shouldBe Seq(Nil -> PlanOp.Empty)
    PlanOp.flatWithCoordinates(PlanOp.seq(plan1, plan2)) shouldBe Seq(
      List(0) -> plan1,
      List(1) -> plan2
    )
    PlanOp.flatWithCoordinates(
      PlanOp.seq(
        PlanOp.seq(
          plan1,
          plan2
        ),
        plan3,
        PlanOp.seq(
          plan4
        )
      )
    ) shouldBe Seq(
      List(0, 0) -> plan1,
      List(0, 1) -> plan2,
      List(1) -> plan3,
      List(2, 0) -> plan4
    )
  }
}

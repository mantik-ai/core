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
package ai.mantik.ds.element

import ai.mantik.ds.{FundamentalType, TypeSamples}
import ai.mantik.testutils.TestBase

class ElementOrderingSpec extends TestBase {

  it should "work for primitives" in {
    TypeSamples.fundamentalSamples.foreach { case (ft, sample) =>
      val ordering = ElementOrdering.elementOrdering(ft)
      withClue(s"it works for ${ft}") {
        ordering.lt(sample, sample) shouldBe false
        ordering.gteq(sample, sample) shouldBe true
      }
    }
  }

  it should "work for non-primitives" in {
    TypeSamples.nonFundamentals.foreach { case (dt, sample) =>
      val ordering = ElementOrdering.elementOrdering(dt)
      withClue(s"it works for ${dt}") {
        ordering.lt(sample, sample) shouldBe false
        ordering.gteq(sample, sample) shouldBe true
      }
    }
  }
}

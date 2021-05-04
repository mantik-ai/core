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

import ai.mantik.ds.FundamentalType.VoidType
import ai.mantik.ds.TypeSamples
import ai.mantik.ds.testutil.TestBase

class PrimitiveEncoderSpec extends TestBase {

  it should "encode and decode all samples" in {
    TypeSamples.fundamentalSamples.foreach { case (typeName, sample) =>
      val encoder = PrimitiveEncoder.lookup(typeName)
      withClue(s"It must work for ${typeName}") {
        encoder.wrap(sample.x.asInstanceOf[encoder.ScalaType]) shouldBe sample
        encoder.unwrap(sample) shouldBe sample.x
      }
    }
  }

  it should "decode from string" in {
    TypeSamples.fundamentalSamples.foreach { case (typeName, sample) =>
      val s = sample.x.toString
      val encoder = PrimitiveEncoder.lookup(typeName)
      withClue(s"It must work for ${typeName}") {
        encoder.convert.isDefinedAt(s) shouldBe true
        encoder.convert(s) shouldBe sample.x
      }
    }
  }

  it should "decode from their own type" in {
    TypeSamples.fundamentalSamples.foreach { case (typeName, sample) =>
      val encoder = PrimitiveEncoder.lookup(typeName)
      withClue(s"It must work for ${typeName}") {
        encoder.convert.isDefinedAt(sample.x) shouldBe true
        encoder.convert(sample.x) shouldBe sample.x
      }
    }
  }
}

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
package ai.mantik.ds.sql

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.FundamentalType
import ai.mantik.testutils.TestBase

class QueryTabularTypeSpec extends TestBase {

  "shadow" should "work" in {
    val input = QueryTabularType(
      "a" -> FundamentalType.Int32,
      "b" -> FundamentalType.Int32,
      "a" -> FundamentalType.Int32,
      "c" -> FundamentalType.StringType,
      "a" -> FundamentalType.BoolType
    )
    input.shadow() shouldBe QueryTabularType(
      "a" -> FundamentalType.Int32,
      "b" -> FundamentalType.Int32,
      "a0" -> FundamentalType.Int32,
      "c" -> FundamentalType.StringType,
      "a1" -> FundamentalType.BoolType
    )
    input.shadow(fromLeft = false) shouldBe QueryTabularType(
      "a1" -> FundamentalType.Int32,
      "b" -> FundamentalType.Int32,
      "a0" -> FundamentalType.Int32,
      "c" -> FundamentalType.StringType,
      "a" -> FundamentalType.BoolType
    )
  }

  it should "bail out if it is too complex" in {
    intercept[FeatureNotSupported] {
      QueryTabularType(
        { for (i <- 0 until 300) yield ("a" -> FundamentalType.Int32) }: _*
      ).shadow()
    }
  }

  it should "respect ignoreAlias" in {
    val sample = QueryTabularType(
      "x" -> FundamentalType.Int32,
      "a.x" -> FundamentalType.Int32,
      "b.x" -> FundamentalType.Int32
    )
    sample.shadow() shouldBe sample
    sample.shadow(ignoreAlias = true) shouldBe QueryTabularType(
      "x" -> FundamentalType.Int32,
      "a.x0" -> FundamentalType.Int32,
      "b.x1" -> FundamentalType.Int32
    )
  }
}

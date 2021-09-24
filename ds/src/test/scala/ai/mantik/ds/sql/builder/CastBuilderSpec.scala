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
package ai.mantik.ds.sql.builder

import ai.mantik.ds.{FundamentalType, Nullable}
import ai.mantik.ds.element.{Bundle, SingleElementBundle}
import ai.mantik.ds.sql.{CastExpression, ColumnExpression, ConstantExpression}
import ai.mantik.testutils.TestBase

class CastBuilderSpec extends TestBase {

  "comparisonType" should "return the same type if equals" in {
    CastBuilder.comparisonType(FundamentalType.Int32, FundamentalType.Int32) shouldBe Right(FundamentalType.Int32)
  }

  it should "choose the next higher same type" in {
    CastBuilder.comparisonType(FundamentalType.Int32, FundamentalType.Int64) shouldBe Right(FundamentalType.Int64)
    CastBuilder.comparisonType(FundamentalType.Int64, FundamentalType.Int32) shouldBe Right(FundamentalType.Int64)
    CastBuilder.comparisonType(FundamentalType.Float32, FundamentalType.Float64) shouldBe Right(FundamentalType.Float64)
    CastBuilder.comparisonType(FundamentalType.Float64, FundamentalType.Float32) shouldBe Right(FundamentalType.Float64)
  }

  it should "figure out matching floating points" in {
    CastBuilder.comparisonType(FundamentalType.Int8, FundamentalType.Float32) shouldBe Right(FundamentalType.Float32)
    CastBuilder.comparisonType(FundamentalType.Int32, FundamentalType.Float32) shouldBe Right(FundamentalType.Float64)
    CastBuilder.comparisonType(FundamentalType.Int64, FundamentalType.Float64).forceLeft
  }

  it should "handle nullable values" in {
    CastBuilder.comparisonType(Nullable(FundamentalType.Int32), FundamentalType.Int8) shouldBe Right(
      Nullable(FundamentalType.Int32)
    )
    CastBuilder.comparisonType(FundamentalType.Int32, Nullable(FundamentalType.Int8)) shouldBe Right(
      Nullable(FundamentalType.Int32)
    )
  }

  "wrapType" should "wrap a type in a cast" in {
    CastBuilder.wrapType(ColumnExpression(1, FundamentalType.Int32), FundamentalType.Float64) shouldBe
      Right(CastExpression(ColumnExpression(1, FundamentalType.Int32), FundamentalType.Float64))
  }

  it should "do nothing if the type is ok" in {
    CastBuilder.wrapType(ColumnExpression(1, FundamentalType.Int32), FundamentalType.Int32) shouldBe
      Right(ColumnExpression(1, FundamentalType.Int32))
  }

  it should "auto convert constants" in {
    CastBuilder.wrapType(
      ConstantExpression(Bundle.fundamental(100: Byte)),
      FundamentalType.Int32
    ) shouldBe Right(ConstantExpression(Bundle.fundamental(100)))
  }

  it should "fail for impossible casts" in {
    CastBuilder
      .wrapType(
        ColumnExpression(1, FundamentalType.VoidType),
        FundamentalType.Int32
      )
      .forceLeft
  }
}

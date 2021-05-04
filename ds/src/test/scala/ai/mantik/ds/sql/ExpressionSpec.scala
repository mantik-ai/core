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

import ai.mantik.ds.{ArrayT, FundamentalType, Nullable}
import ai.mantik.ds.element.{ArrayElement, NullElement, SingleElementBundle, SomeElement}
import ai.mantik.ds.testutil.TestBase

class ExpressionSpec extends TestBase {

  "ArrayGetExpression" should "have the correct type" in {
    ArrayGetExpression(
      ConstantExpression(SingleElementBundle(ArrayT(FundamentalType.StringType), ArrayElement())),
      ConstantExpression(0)
    ).dataType shouldBe Nullable(FundamentalType.StringType)

    ArrayGetExpression(
      ConstantExpression(SingleElementBundle(Nullable(ArrayT(FundamentalType.StringType)), NullElement)),
      ConstantExpression(0)
    ).dataType shouldBe Nullable(FundamentalType.StringType)
  }

  "SizeExpression" should "have the correct type" in {
    SizeExpression(
      ConstantExpression(SingleElementBundle(ArrayT(FundamentalType.StringType), ArrayElement()))
    ).dataType shouldBe FundamentalType.Int32

    SizeExpression(
      ConstantExpression(SingleElementBundle(Nullable(ArrayT(FundamentalType.StringType)), SomeElement(ArrayElement())))
    ).dataType shouldBe Nullable(FundamentalType.Int32)
  }
}

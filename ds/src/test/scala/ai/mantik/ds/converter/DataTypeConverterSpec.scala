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
package ai.mantik.ds.converter

import ai.mantik.ds.converter.DataTypeConverter.IdentityConverter
import ai.mantik.ds.element.{EmbeddedTabularElement, Primitive, TabularRow}
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{FundamentalType, TabularData}

class DataTypeConverterSpec extends TestBase {

  "IdentityConverter" should "always return the input" in {
    val foo = IdentityConverter(FundamentalType.Int32)
    foo.targetType shouldBe FundamentalType.Int32
    foo.convert(Primitive(3)) shouldBe Primitive(3)
  }

  "ConstantConverter" should "always return constnts" in {
    val c = DataTypeConverter.ConstantConverter(
      FundamentalType.Int32,
      Primitive(3)
    )
    c.targetType shouldBe FundamentalType.Int32
    c.convert(Primitive(4)) shouldBe Primitive(3)
  }

  "fundamental" should "convert with a function" in {
    val c = DataTypeConverter.fundamental(FundamentalType.Int32) { x: Int => x + 1 }
    c.targetType shouldBe FundamentalType.Int32
    c.convert(Primitive(4)) shouldBe Primitive(5)
  }

  "TabularConverter" should "apply all sub converters" in {
    val t = TabularConverter(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.StringType
      ),
      IndexedSeq(
        DataTypeConverter.fundamental(FundamentalType.Int32) { x: Int => x + 1 },
        DataTypeConverter.ConstantConverter(FundamentalType.StringType, Primitive("boom"))
      )
    )
    val row = TabularRow(
      Primitive(3),
      Primitive("in")
    )
    val expected = TabularRow(
      Primitive(4),
      Primitive("boom")
    )

    t.convert(
      EmbeddedTabularElement(Vector(row))
    ) shouldBe EmbeddedTabularElement(Vector(expected))

    t.convert(row) shouldBe expected
  }
}

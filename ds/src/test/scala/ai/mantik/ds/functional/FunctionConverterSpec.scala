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
package ai.mantik.ds.functional

import ai.mantik.ds.Errors.DataTypeMismatchException
import ai.mantik.ds.element.{NullElement, SomeElement, TabularRow}
import ai.mantik.ds.{FundamentalType, Nullable, TabularData}
import ai.mantik.testutils.TestBase

class FunctionConverterSpec extends TestBase {
  it should "work for primitives" in {
    val fc = implicitly[FunctionConverter[Int, String]]
    fc.destinationTypeAsTable() shouldBe TabularData(
      "_1" -> FundamentalType.StringType
    )

    fc.buildDecoderForTables(
      TabularData(
        "a" -> FundamentalType.Int32
      )
    )(TabularRow(12)) shouldBe 12

    fc.buildDecoderForTables(
      TabularData(
        "a" -> FundamentalType.Int32,
        "b" -> FundamentalType.StringType
      )
    )(TabularRow(12, "Hello")) shouldBe 12

    fc.buildEncoderForTables()("Hello World") shouldBe TabularRow("Hello World")
  }

  it should "work for tuples" in {
    val fc = implicitly[FunctionConverter[(Int, Int), (String, Double)]]

    fc.destinationTypeAsTable() shouldBe TabularData(
      "_1" -> FundamentalType.StringType,
      "_2" -> FundamentalType.Float64
    )

    fc.buildDecoderForTables(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.Int32
      )
    )(TabularRow(1, 2)) shouldBe (1, 2)

    fc.buildEncoderForTables()("Hello", 3.14) shouldBe TabularRow("Hello", 3.14)
  }

  it should "automatically cast" in {
    val fc = implicitly[FunctionConverter[Int, Double]]
    val decoder = fc.buildDecoderForTables(
      TabularData(
        "x" -> FundamentalType.Float64
      )
    )
    decoder(TabularRow(3.14)) shouldBe 3
  }

  it should "throw if the number of elements is too narrow" in {
    val fc = implicitly[FunctionConverter[(Int, Int, Int), Int]]
    intercept[DataTypeMismatchException] {
      fc.buildDecoderForTables(
        TabularData(
          "x" -> FundamentalType.Int32,
          "y" -> FundamentalType.Int32
        )
      )
    }.getMessage should include("not enough columns")
  }

  it should "work for nullables" in {
    val fc = implicitly[FunctionConverter[Option[Int], Option[String]]]
    val decoder = fc.buildDecoderForTables(
      TabularData(
        "a" -> Nullable(FundamentalType.Int32)
      )
    )

    decoder(TabularRow(SomeElement(12))) shouldBe Some(12)
    decoder(TabularRow(NullElement)) shouldBe None

    val encoder = fc.buildEncoderForTables()
    encoder(None) shouldBe TabularRow(NullElement)
    encoder(Some("Foo")) shouldBe TabularRow(SomeElement("Foo"))
  }
}

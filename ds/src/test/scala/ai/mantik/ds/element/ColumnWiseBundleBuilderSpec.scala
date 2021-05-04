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

import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.testutils.TestBase

class ColumnWiseBundleBuilderSpec extends TestBase {

  it should "create nice bundles" in {
    val bundle = ColumnWiseBundleBuilder()
      .withColumn("x", FundamentalType.StringType, Vector(Primitive("a"), Primitive("b")))
      .withPrimitives("y", 1, 2)
      .result

    bundle shouldBe TabularBundle(
      TabularData(
        "x" -> FundamentalType.StringType,
        "y" -> FundamentalType.Int32
      ),
      rows = Vector(
        TabularRow(
          IndexedSeq(Primitive("a"), Primitive(1))
        ),
        TabularRow(
          IndexedSeq(Primitive("b"), Primitive(2))
        )
      )
    )
  }

  it should "detect row count mismatch" in {
    val builder = ColumnWiseBundleBuilder()
      .withPrimitives("x", 1, 2)

    intercept[IllegalArgumentException] {
      builder.withPrimitives("y", 3, 4, 5)
    }
  }
}

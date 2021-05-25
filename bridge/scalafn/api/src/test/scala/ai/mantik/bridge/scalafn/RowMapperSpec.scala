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
package ai.mantik.bridge.scalafn

import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.ds.element.{Primitive, TabularRow}
import ai.mantik.testutils.TestBase

class RowMapperSpec extends TestBase {

  it should "serialize a simple function" in {
    val fn: TabularRow => TabularRow = { row =>
      val left = row.columns.head.asInstanceOf[Primitive[Int]].x
      val right = row.columns(1).asInstanceOf[Primitive[Int]].x
      val result = left + right
      val encoded = TabularRow(result)
      encoded
    }
    val rowMapper = RowMapper(fn)
    val serialized = rowMapper.serialize()
    val result = ScalaFnPayload.deserialize(serialized)
    val deserialized = result.payload.asInstanceOf[RowMapper]
    deserialized.f(TabularRow(Primitive(1), Primitive(2))) shouldBe TabularRow(Primitive(3))
    result.close()
  }

  it should "work for a more complex function" in {
    val fn: TabularRow => TabularRow = { row =>
      TabularRow(
        // TODO: #203 Add helper for this kind of extraction and building
        row.columns(0).asInstanceOf[Primitive[Int]].x + 1,
        row.columns(1).asInstanceOf[Primitive[String]].x + "!"
      )
    }
    val rowMapper = RowMapper(fn)
    val serialized = rowMapper.serialize()
    val result = ScalaFnPayload.deserialize(serialized)
    val deserialized = result.payload.asInstanceOf[RowMapper]
    deserialized.f(TabularRow(Primitive(1), Primitive("a"))) shouldBe TabularRow(Primitive(2), Primitive("a!"))
    result.close()
  }

  it should "use the nice auto api" in {
    val input = TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.Int32
    )
    val rowMapper = RowMapper.build[(Int, Int), (Int, String)](input) { case (x, y) =>
      (x - y, x.toString)
    }
    val serialized = rowMapper.serialize()
    val back = ScalaFnPayload.deserialize(serialized)
    val backFn = back.payload.asInstanceOf[RowMapper]
    backFn.f(TabularRow(3, 2)) shouldBe TabularRow(1, "3")
    back.close()

  }
}

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
package ai.mantik.planner.integration

import ai.mantik.bridge.scalafn.{RowMapper, UserDefinedFunction}
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.ds.element.{Primitive, TabularBundle, TabularRow}
import ai.mantik.planner.DataSet
import ai.mantik.planner.integration.ScalaFnBridgeSpec.mapFunction1
import com.twitter.chill.ClosureCleaner

import scala.util.Random

object ScalaFnBridgeSpec {
  val fn: TabularRow => TabularRow = { row =>
    TabularRow(
      // TODO: Add helper for this kind of extraction and building #203
      row.columns(0).asInstanceOf[Primitive[Int]].x + 1,
      row.columns(1).asInstanceOf[Primitive[String]].x + "!"
    )
  }
  val mapFunction1 = RowMapper(fn)
}

class ScalaFnBridgeSpecBigClosure(randomData: Vector[Int]) {
  val fn: TabularRow => TabularRow = { row =>
    val input = row.columns(0).asInstanceOf[Primitive[Int]].x
    val reduced = randomData.sum + input
    TabularRow(Primitive(reduced))
  }
}

object ScalaFnBridgeSpecOther {
  val underlying2: (Int, Int) => (Int, Option[String]) = { (a, b) =>
    val s = if (b % 2 == 0) {
      Some("even")
    } else {
      None
    }
    (a + b, s)
  }
  val fn2 = UserDefinedFunction(underlying2)
}

class ScalaFnBridgeSpec extends IntegrationTestBase {
  trait Env {}

  it should "do a simple row mapping" in new Env {
    val input = TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3)
      .withPrimitives("y", "a", "b", "c")
      .result

    val inputDs = DataSet.literal(input)

    val result = inputDs.mapRows(
      TabularData(
        "a" -> FundamentalType.Int32,
        "b" -> FundamentalType.StringType
      )
    )(mapFunction1)
    val fetched = result.fetch.run()
    fetched shouldBe TabularBundle.buildColumnWise
      .withPrimitives("a", 2, 3, 4)
      .withPrimitives("b", "a!", "b!", "c!")
      .result
  }

  it should "work with big closures" in new Env {
    val input = TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3)
      .result

    val inputDs = DataSet.literal(input)
    val data = Random.shuffle((0 until 100000).toVector)
    val holder = new ScalaFnBridgeSpecBigClosure(data)
    inputDs.mapRows(TabularData("x" -> FundamentalType.Int32))(RowMapper(holder.fn)).fetch.run()
  }

  it should "work with scala functions" in new Env {
    val input = TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3)
      .withPrimitives("y", 3, 4, 5)
      .result

    val inputDs = DataSet.literal(input)
    // TODO: Even simplify function signature more..
    val got = inputDs.mapRowsFn(ScalaFnBridgeSpecOther.fn2).fetch.run()

    val expected = TabularBundle.buildColumnWise
      .withPrimitives("_1", 4, 6, 8)
      .withPrimitives("_2", None, Some("even"), None)
      .result

    got shouldBe expected
  }
}

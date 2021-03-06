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

import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.ds.element.{Primitive, TabularBundle, TabularRow}
import ai.mantik.planner.DataSet

class SplitSpec extends IntegrationTestBase {

  val sample = TabularBundle(
    TabularData(
      "x" -> FundamentalType.Int32,
      "name" -> FundamentalType.StringType
    ),
    (for (i <- 0 until 100) yield {
      TabularRow(Primitive(i), Primitive(s"Element ${i}"))
    }).toVector
  )

  it should "it should be possible to split a dataset" in {
    val dataset = DataSet.literal(sample)

    val Seq(a, b, c) = dataset.split(Seq(0.5, 0.2))

    b.isCached shouldBe true
    b.state.isCacheEvaluated shouldBe false

    a.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(0, 50)
    )

    b.state.isCacheEvaluated shouldBe true

    b.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(50, 70)
    )
    c.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(70, 100)
    )
  }

  it should "be possible to shuffle" in {
    val dataset = DataSet.literal(sample)

    val Seq(a, b) = dataset.split(Seq(0.5), shuffleSeed = Some(1))
    val aFetched = a.fetch.run()
    aFetched.rows.size shouldBe 50
    aFetched.rows shouldNot be(sample.rows.slice(0, 50))

    val bFetched = b.fetch.run()
    bFetched.rows.size shouldBe 50
    bFetched.rows shouldNot be(sample.rows.slice(50, 100))

    val allRows = aFetched.rows.map(_.asInstanceOf[TabularRow]) ++
      bFetched.rows.map(_.asInstanceOf[TabularRow])

    TabularBundle(
      sample.model,
      allRows
    ).sorted shouldBe sample
  }

  it should "clamp values out of range" in {
    val dataset = DataSet.literal(sample)
    val Seq(a, b) = dataset.split(Seq(1.3))
    a.fetch.run().rows.size shouldBe 100
    b.fetch.run().rows.size shouldBe 0

    val Seq(c, d) = dataset.split(Seq(-0.4))
    c.fetch.run().rows.size shouldBe 0
    d.fetch.run().rows.size shouldBe 100
  }

  it should "support single split" in {
    val dataset = DataSet.literal(sample)
    val Seq(a) = dataset.split(Seq.empty, shuffleSeed = Some(2))
    val aResult = a.fetch.run()
    aResult.rows.size shouldBe 100
    val tabularRows = aResult.rows.map(_.asInstanceOf[TabularRow])
    tabularRows shouldNot be(sample.rows)
    TabularBundle(
      sample.model,
      tabularRows
    ).sorted shouldBe sample.sorted
  }

  it should "be possible to disable caching" in {
    val dataset = DataSet.literal(sample)

    val Seq(a, b) = dataset.split(Seq(0.5), cached = false)

    b.isCached shouldBe false
    b.state.isCacheEvaluated shouldBe false

    a.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(0, 50)
    )
    b.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(50, 100)
    )

    b.state.isCacheEvaluated shouldBe false
  }
}

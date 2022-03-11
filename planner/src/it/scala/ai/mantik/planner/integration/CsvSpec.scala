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
import ai.mantik.ds.element.TabularBundle
import ai.mantik.planner.DataSet
import ai.mantik.planner.csv.CsvOptions

import java.nio.file.Paths

class CsvSpec extends IntegrationTestBase with Samples {
  trait Env extends EnvWithBridges {}

  val helloWorldBundle = TabularBundle.buildColumnWise
    .withPrimitives(
      "name",
      "Alice",
      "Bob",
      "Charly",
      "Dave",
      "Emi\"l"
    )
    .withPrimitives(
      "age",
      42,
      13,
      14,
      15,
      16
    )
    .result

  it should "read CSV items from local directories" in new Env {
    val helloWorldId = context.pushLocalMantikItem(Paths.get("bridge/csv/examples/01_helloworld"))
    val dataSet = context.loadDataSet(helloWorldId)
    val got = dataSet.fetch.run()
    got shouldBe helloWorldBundle
  }

  it should "read CSV files from serialized local files" in new Env {
    val dataSet = DataSet.csv
      .file("bridge/csv/examples/01_helloworld/payload", CsvOptions(skipHeader = Some(true)))
      .withFormat(helloWorldBundle.model)
      .load()
    val got = dataSet.fetch.run()
    got shouldBe helloWorldBundle
  }

  it should "read customized CSV Files" in new Env {
    val dataSet = DataSet.csv
      .file("bridge/csv/examples/03_customized/payload", CsvOptions(comma = Some("k"), skipHeader = Some(false)))
      .withFormat(TabularData("x" -> FundamentalType.StringType, "y" -> FundamentalType.Int32))
      .load()
    val got = dataSet.fetch.run()
    val sample = TabularBundle
      .build(dataSet.forceDataTypeTabular())
      .row("Alice", 100)
      .row("Bob", 200)
      .row("Charly", 300)
      .result
    got shouldBe sample
  }

  it should "read CSV Files from URLs" in new Env {
    // From: https://people.sc.fsu.edu/~jburkardt/data/csv/csv.html
    val dataSet = DataSet.csv
      .url("https://mantik-public-testdata.s3.eu-central-1.amazonaws.com/deniro.csv")
      .skipHeader
      .withFormat(
        TabularData(
          "Year" -> FundamentalType.Int32,
          "Score" -> FundamentalType.Int32,
          "Title" -> FundamentalType.StringType
        )
      )
      .load()
    dataSet.fetch.run().rows.size shouldBe 87
  }
}

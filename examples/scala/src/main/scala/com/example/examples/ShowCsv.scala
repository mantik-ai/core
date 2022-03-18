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
package com.example.examples
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.planner.{DataSet, PlanningContext}

object ShowCsv extends ExampleBase {
  override protected def run(implicit context: PlanningContext): Unit = {
    val sample = DataSet.csv
      .file("bridge/csv/examples/01_helloworld/payload")
      .skipHeader
      .withFormat(
        TabularData(
          "Name" -> FundamentalType.StringType,
          "Age" -> FundamentalType.Int32
        )
      )
      .load()

    val fetched = sample.fetch.run()
    println(s"Fetched: ${fetched.render()}")
  }
}

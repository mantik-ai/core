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
import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.planner.{DataSet, PlanningContext}

object SelectSample extends ExampleBase {

  override protected def run(implicit context: PlanningContext): Unit = {
    val bundle = TabularBundle
      .build(
        TabularData(
          "x" -> FundamentalType.Int32,
          "y" -> FundamentalType.Int32,
          "s" -> FundamentalType.StringType
        )
      )
      .row(100, 200, "Hello")
      .row(100, 300, "World")
      .row(200, 400, "!!!!")
      .result

    val literal = DataSet.literal(bundle)

    val selected1 = literal.select(
      "select x + y as sum, s where x = 100"
    )

    val selected2 = literal.select(
      "select *"
    )

    val selected3 = literal.select(
      "select x * y"
    )

    val result1 = selected1.fetch.run()
    println("Result1\n" + result1.render())

    val result2 = selected2.fetch.run()
    println("Result2\n" + result2.render())

    val result3 = selected3.fetch.run()
    println("Result3\n" + result3.render())
  }
}

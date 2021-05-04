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

object ShowDataSet extends ExampleBase {

  override protected def run(implicit context: PlanningContext): Unit = {
    val id = "sample1"

    val ds = DataSet
      .literal(
        TabularBundle
          .build(
            TabularData(
              "x" -> FundamentalType.Int32,
              "y" -> FundamentalType.StringType
            )
          )
          .row(1, "Hello")
          .row(2, "World")
          .result
      )
      .tag(id)

    ds.save().run()

    val result = context.loadDataSet(id).fetch.run()

    println(result)
  }
}

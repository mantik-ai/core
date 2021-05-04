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

object CacheExample extends ExampleBase {

  override protected def run(implicit context: PlanningContext): Unit = {

    val sample = DataSet.literal(
      TabularBundle
        .build(
          TabularData("x" -> FundamentalType.Int32)
        )
        .row(1)
        .row(2)
        .row(3)
        .result
    )

    val transformed = sample.select("select x + 1 as y").cached

    println(transformed.fetch.run())
    println(transformed.fetch.run())

    val extraTransformation = transformed.select("select y + 1 as z")
    println(extraTransformation.fetch.run())
  }
}

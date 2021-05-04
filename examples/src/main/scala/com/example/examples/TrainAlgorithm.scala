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

import java.io.File
import ai.mantik.ds.FundamentalType.{Float64, Int32}
import ai.mantik.ds.{TabularData, Tensor}
import ai.mantik.ds.element.{Bundle, TabularBundle, TensorElement}
import ai.mantik.planner.{DataSet, PlanningContext}

object TrainAlgorithm extends ExampleBase {

  override protected def run(implicit context: PlanningContext): Unit = {
    val sampleFile = new File("bridge/sklearn/simple_learn/example/kmeans").toPath
    context.pushLocalMantikItem(sampleFile)

    def makeTensor(a: Double, b: Double): TensorElement[Double] = TensorElement(IndexedSeq(a, b))

    val learningData: Bundle = TabularBundle
      .build(
        TabularData(
          "coordinates" -> Tensor(componentType = Float64, shape = List(2))
        )
      )
      .row(makeTensor(1, 1))
      .row(makeTensor(2, 2))
      .row(makeTensor(1, 2))
      .row(makeTensor(2, 2))
      .row(makeTensor(3, 3))
      .row(makeTensor(3, 4))
      .row(makeTensor(4, 3))
      .row(makeTensor(4, 4))
      .result

    val kmeans = context.loadTrainableAlgorithm("kmeans")

    val (trained, stats) = kmeans.train(DataSet.literal(learningData))

    trained.tag("kmeans_trained").save().run()

    val trainedAgain = context.loadAlgorithm("kmeans_trained")

    val sampleData = TabularBundle
      .build(
        TabularData(
          "coordinates" -> Tensor(componentType = Float64, shape = List(2))
        )
      )
      .row(makeTensor(1, 1))
      .row(makeTensor(0, 0))
      .row(makeTensor(4, 4))
      .result

    val applied =
      trainedAgain(DataSet.literal(sampleData)).fetch.run()

    println(applied)

    val statsFetched =
      stats.fetch.run()

    println("Stats\n" + statsFetched)
  }
}

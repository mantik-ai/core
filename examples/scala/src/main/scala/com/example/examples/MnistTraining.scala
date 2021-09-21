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
import java.nio.file.Paths

import ai.mantik.componently.utils.EitherExtensions._
import ai.mantik.ds.sql.AutoSelect
import ai.mantik.ds.{FundamentalType, Image, ImageChannel, TabularData}
import ai.mantik.planner.{Algorithm, Pipeline, PlanningContext}

object MnistTraining extends ExampleBase {

  val MnistTrainingPath = Paths.get("bridge/binary/test/mnist_train")
  val MnistTestPath = Paths.get("bridge/binary/test/mnist")

  val TrainingAlgorithmPath = Paths.get("bridge/tf/train/example/mnist_linear")

  override protected def run(implicit context: PlanningContext): Unit = {
    context.pushLocalMantikItem(MnistTrainingPath)
    context.pushLocalMantikItem(TrainingAlgorithmPath)
    context.pushLocalMantikItem(MnistTestPath)

    // Training
    val trainDataSet = context.loadDataSet("mnist_train")

    val trainAlgorithm = context
      .loadTrainableAlgorithm("mnist_linear")
      .withMetaValue("n_epochs", 5)

    val (trained, stats) = trainAlgorithm.train(trainDataSet)

    // Evaluating
    val testDataSet = context.loadDataSet("mnist_test")
    val adaptedTest = testDataSet.select("select x as image")
    val applied = trained.apply(adaptedTest)

    val appliedResult = applied.fetch.run("Train MNIST")
    println("Applied:\n" + appliedResult.render())

    println("Stats:\n" + stats.fetch.run("Fetching Stats"))

    // Building a Pipeline
    val productionImageInput = TabularData(
      "image" -> Image.plain(
        28,
        28,
        ImageChannel.Black -> FundamentalType.Uint8
      )
    )

    val inputFilter =
      AutoSelect.autoSelect(productionImageInput, trained.functionType.input).force

    val productionPipe = Pipeline.build(
      Left(inputFilter),
      Right(trained)
    )

    // Deploying
    val deployResult = productionPipe.deploy(ingressName = Some("mnist")).run("Deploying MNIST")
    println(s"Pipeline deployed: ${deployResult.externalUrl}")
  }
}

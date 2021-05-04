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

import ai.mantik.ds.FundamentalType.Float64
import ai.mantik.ds.Tensor
import ai.mantik.ds.element.{Bundle, TabularBundle, TensorElement}
import ai.mantik.planner.{DataSet, Pipeline}

class AnonymousPipelineSpec extends IntegrationTestBase with Samples {

  def makeTensor(a: Double, b: Double): TensorElement[Double] = TensorElement(IndexedSeq(a, b))

  it should "save and load a pipeline with anonymous elements in it" in new EnvWithBridges {
    context.pushLocalMantikItem(kmeansDirectory)

    val learningData = TabularBundle.buildColumnWise
      .withColumn(
        "coordinates",
        Tensor(componentType = Float64, shape = List(2)),
        IndexedSeq(
          makeTensor(1.0, 2.0),
          makeTensor(2.0, 3.0),
          makeTensor(0.0, 4.0),
          makeTensor(2.0, 3.0)
        )
      )
      .result

    val kmeans = context.loadTrainableAlgorithm("kmeans")

    // kmeans will end up as an anonymous algorithm inside the pipeline.

    val (trained, _) = kmeans.train(DataSet.literal(learningData))

    val pipeline = Pipeline.build(trained).tag("pipeline1234")
    context.execute(pipeline.save())

    val sampleData = TabularBundle
      .build(learningData.model)
      .row(makeTensor(1.0, 2.0))
      .row(makeTensor(0.0, 3.0))
      .result

    val pipeline2 = context.loadPipeline("pipeline1234")
    val used = context.execute(pipeline2.apply(DataSet.literal(sampleData)).fetch).asInstanceOf[TabularBundle]

    used.rows.size shouldBe 2
    used.model.lookupColumnIndex("label") shouldBe Some(0)
  }
}

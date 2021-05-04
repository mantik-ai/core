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

import ai.mantik.ds.TabularData
import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.ds.sql.Select
import ai.mantik.planner.{Algorithm, DataSet, Pipeline}

class SimplePipelineSpec extends IntegrationTestBase with Samples {

  trait Env extends EnvWithBridges {
    context.pushLocalMantikItem(doubleMultiplyDirectory)

    val doubleMultiply = context.loadAlgorithm("double_multiply")
    val toStringConversion =
      Select
        .parse(doubleMultiply.functionType.output.asInstanceOf[TabularData], "select CAST (y as int32) AS z")
        .right
        .get

    val pipeline = Pipeline.build(
      Right(doubleMultiply),
      Left(toStringConversion)
    )

    val input = TabularBundle.buildColumnWise
      .withPrimitives("x", 1.0, 2.0)
      .result

    val inputDataSet = DataSet.literal(input)

    val expectedOutput = TabularBundle.buildColumnWise
      .withPrimitives("z", 2, 4)
      .result
  }

  it should "construct and execute simple pipelines" in new Env {
    val applied = pipeline.apply(inputDataSet)
    val got = context.execute(applied.fetch)

    got shouldBe expectedOutput
  }

  it should "be possible to save and restore a pipeline" in new Env {
    context.execute(pipeline.tag("pipeline1").save())
    val loadedAgain = context.loadPipeline("pipeline1")

    val applied = loadedAgain.apply(inputDataSet)
    val got = context.execute(applied.fetch)

    got shouldBe expectedOutput
  }
}

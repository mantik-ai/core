package com.example.examples

import java.io.File
import java.nio.file.Paths

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.planner.{ PlanningContext, DataSet }

object DataSetTransformation extends ExampleBase {

  override protected def run(implicit context: PlanningContext): Unit = {
    val bridge = Paths.get("bridge/tf/saved_model")
    val sampleFile = Paths.get("bridge/tf/saved_model/test/resources/samples/double_multiply")
    context.pushLocalMantikItem(bridge)
    context.pushLocalMantikItem(sampleFile)

    val dataset = DataSet.literal(
      Bundle.build(
        TabularData(
          "x" -> FundamentalType.Float64
        )
      )
        .row(1.0)
        .row(2.0).result
    )

    val transformation = context.loadAlgorithm("double_multiply")
    val result = transformation(dataset).fetch.run()

    println(s"Result\n$result")
  }
}

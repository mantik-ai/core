package com.example.examples

import java.io.File

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.planner.{ Context, DataSet }

object DataSetTransformation {

  def main(args: Array[String]): Unit = {
    val context: Context = Context.local()
    try {
      // TODO: Copy test into the same directory
      val sampleFile = new File("../bridge/bridge/tf/saved_model/test/resources/samples/double_multiply").toPath
      context.pushLocalMantikFile(sampleFile)

      val dataset = DataSet.literal(
        Bundle.build(
          TabularData(
            "x" -> FundamentalType.Float64
          )
        )
          .row(1.0)
          .row(2.0).result
      )

      val transformation = context.loadTransformation("double_multiply")
      val result = context.execute(
        transformation(dataset).fetch
      )
      println(s"Result\n $result")
    } finally {
      context.shutdown()
    }
  }
}

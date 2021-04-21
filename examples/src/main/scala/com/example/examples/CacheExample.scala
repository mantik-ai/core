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

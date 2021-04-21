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

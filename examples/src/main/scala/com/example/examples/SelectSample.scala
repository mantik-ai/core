package com.example.examples
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.planner.{ Context, DataSet }

object SelectSample extends ExampleBase {

  override protected def run(context: Context): Unit = {
    val bundle = Bundle.build(
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

    val result1 = context.execute(selected1.fetch)
    println("Result1\n" + result1.render())

    val result2 = context.execute(selected2.fetch)
    println("Result2\n" + result2.render())

    val result3 = context.execute(selected3.fetch)
    println("Result3\n" + result3.render())
  }
}

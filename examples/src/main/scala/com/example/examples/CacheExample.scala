package com.example.examples
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.planner.{ Context, DataSet }

object CacheExample extends ExampleBase {

  override protected def run(implicit context: Context): Unit = {

    val sample = DataSet.literal(
      Bundle.build(
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

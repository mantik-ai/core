package com.example.examples

import ai.mantik.core.{ Context, DataSet }
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.natural.NaturalBundle

object ShowDataSet {

  def main(args: Array[String]): Unit = {
    val context: Context = Context.local()
    try {
      val ds = DataSet.literal(
        NaturalBundle.build(
          TabularData(
            "x" -> FundamentalType.Int32,
            "y" -> FundamentalType.StringType
          )
        ).row(1, "Hello")
          .row(2, "World")
          .result
      )
      val id = "sample1"

      context.execute(
        ds.save(id)
      )

      val result = context.execute(
        context.loadDataSet(id).fetch
      )

      println(result.model)
      println(result.rows)

    } finally {
      context.shutdown()
    }
  }
}

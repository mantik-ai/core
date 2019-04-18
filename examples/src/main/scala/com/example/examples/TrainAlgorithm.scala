package com.example.examples

import java.io.File

import ai.mantik.ds.FundamentalType.{ Float64, Int32 }
import ai.mantik.ds.{ TabularData, Tensor }
import ai.mantik.ds.element.{ Bundle, TensorElement }
import ai.mantik.planner.{ Context, DataSet }

object TrainAlgorithm {

  def main(args: Array[String]): Unit = {
    val context: Context = Context.local()
    try {
      val sampleFile = new File("bridge/sklearn/simple_learn/example/kmeans").toPath
      context.pushLocalMantikFile(sampleFile)

      def makeTensor(a: Double, b: Double): TensorElement[Double] = TensorElement(IndexedSeq(a, b))

      val learningData: Bundle = Bundle.build(
        TabularData(
          "coordinates" -> Tensor(componentType = Float64, shape = List(2))
        )
      )
        .row(makeTensor(1, 1))
        .row(makeTensor(2, 2))
        .row(makeTensor(1, 2))
        .row(makeTensor(2, 2))
        .row(makeTensor(3, 3))
        .row(makeTensor(3, 4))
        .row(makeTensor(4, 3))
        .row(makeTensor(4, 4))
        .result

      val kmeans = context.loadTrainableAlgorithm("kmeans")

      val (trained, stats) = kmeans.train(DataSet.literal(learningData))

      context.execute(
        trained.save("kmeans_trained")
      )

      val trainedAgain = context.loadTransformation("kmeans_trained")

      val sampleData = Bundle.build(
        TabularData(
          "coordinates" -> Tensor(componentType = Float64, shape = List(2))
        )
      )
        .row(makeTensor(1, 1))
        .row(makeTensor(0, 0))
        .row(makeTensor(4, 4))
        .result

      val applied = context.execute(
        trainedAgain(DataSet.literal(sampleData)).fetch
      )

      println(applied)

      val statsFetched = context.execute(
        stats.fetch
      )
      println("Stats\n" + statsFetched)

    } finally {
      context.shutdown()
    }
  }
}

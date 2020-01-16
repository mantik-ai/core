package com.example.examples

import java.io.File

import ai.mantik.ds.FundamentalType.{ Float64, Int32 }
import ai.mantik.ds.{ TabularData, Tensor }
import ai.mantik.ds.element.{ Bundle, TensorElement }
import ai.mantik.planner.{ Context, DataSet }

object TrainAlgorithm extends ExampleBase {

  override protected def run(implicit context: Context): Unit = {
    val sampleFile = new File("bridge/sklearn/simple_learn/example/kmeans").toPath
    context.pushLocalMantikItem(sampleFile)

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

    trained.tag("kmeans_trained").save().run()

    val trainedAgain = context.loadAlgorithm("kmeans_trained")

    val sampleData = Bundle.build(
      TabularData(
        "coordinates" -> Tensor(componentType = Float64, shape = List(2))
      )
    )
      .row(makeTensor(1, 1))
      .row(makeTensor(0, 0))
      .row(makeTensor(4, 4))
      .result

    val applied =
      trainedAgain(DataSet.literal(sampleData)).fetch.run()

    println(applied)

    val statsFetched =
      stats.fetch.run()

    println("Stats\n" + statsFetched)
  }
}

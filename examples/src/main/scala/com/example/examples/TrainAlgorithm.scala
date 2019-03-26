package com.example.examples

import ai.mantik.core.Context

object TrainAlgorithm {

  def main(args: Array[String]): Unit = {
    val context: Context = ???

    val trainingDataSet = context.loadDataSet("train_dataset1")
    val validationDataSet = context.loadDataSet("validation_dataset")
    val trainableAlgorithm = context.loadTrainableAlgorithm("trainable_algorithm")

    val (trained, stats) = trainableAlgorithm.train(trainingDataSet, Some(validationDataSet))
    context.execute(
      trained.save("result_algorithm"),
      stats.save("result_stats")
    )
  }
}

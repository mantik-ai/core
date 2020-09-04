package com.example.examples
import java.nio.file.Paths

import ai.mantik.componently.utils.EitherExtensions._
import ai.mantik.ds.{ FundamentalType, Image, ImageChannel, TabularData }
import ai.mantik.planner.select.AutoAdapt
import ai.mantik.planner.{ PlanningContext, Pipeline }

object MnistTraining extends ExampleBase {

  val MnistTrainingPath = Paths.get("bridge/binary/test/mnist_train")
  val MnistTestPath = Paths.get("bridge/binary/test/mnist")

  val TrainingAlgorithmPath = Paths.get("bridge/tf/train/example/mnist_linear")

  override protected def run(implicit context: PlanningContext): Unit = {
    context.pushLocalMantikItem(MnistTrainingPath)
    context.pushLocalMantikItem(TrainingAlgorithmPath)
    context.pushLocalMantikItem(MnistTestPath)

    // Training
    val trainDataSet = context.loadDataSet("mnist_train")

    val trainAlgorithm = context.loadTrainableAlgorithm("mnist_linear")
      .withMetaValue("n_epochs", 5)

    val (trained, stats) = trainAlgorithm.train(trainDataSet)

    // Evaluating
    val testDataSet = context.loadDataSet("mnist_test")
    val adaptedTest = testDataSet.select("select x as image")
    val applied = trained.apply(adaptedTest)

    val appliedResult = applied.fetch.run()
    println("Applied:\n" + appliedResult.render())

    println("Stats:\n" + context.execute(stats.fetch))

    // Building a Pipeline
    val productionImageInput = TabularData(
      "image" -> Image.plain(
        28, 28, ImageChannel.Black -> FundamentalType.Uint8
      )
    )

    val inputFilter = AutoAdapt.autoSelectAlgorithm(productionImageInput, trained.functionType.input).force

    val productionPipe = Pipeline.build(
      inputFilter,
      trained
    )

    // Deploying
    val deployResult = productionPipe.deploy(ingressName = Some("mnist")).run()
    println(s"Pipeline deployed: ${deployResult.externalUrl}")
  }
}

package com.example.examples
import java.io.File

import ai.mantik.ds.{ FundamentalType, Image, ImageChannel, ImageComponent, TabularData }
import ai.mantik.planner.{ Context, Pipeline }
import ai.mantik.planner.select.AutoAdapt

import ai.mantik.componently.utils.EitherExtensions._
import scala.collection.immutable.ListMap

object MnistTraining extends ExampleBase {

  val MnistTrainingPath = new File("bridge/binary/test/mnist_train").toPath
  val MnistTestPath = new File("bridge/binary/test/mnist").toPath

  val TrainingAlgorithmPath = new File("bridge/tf/train/example/mnist_linear").toPath

  override protected def run(context: Context): Unit = {
    context.pushLocalMantikFile(MnistTrainingPath)
    context.pushLocalMantikFile(TrainingAlgorithmPath)
    context.pushLocalMantikFile(MnistTestPath)

    val trainDataSet = context.loadDataSet("mnist_train")
    val testDataSet = context.loadDataSet("mnist_test")

    val trainAlgorithm = context.loadTrainableAlgorithm("mnist_linear")
      .withMetaValue("n_epochs", 5)

    val (trained, stats) = trainAlgorithm.train(trainDataSet)

    val adaptedTest = testDataSet.select("select x as image")
    val applied = trained.apply(adaptedTest)

    // Mmmm, for a bit better analysis at the end we would need some zip-functionality with expected labels.
    val appliedResult = context.execute(applied.fetch)
    println("Applied:\n" + appliedResult.render())

    println("Stats:\n" + context.execute(stats.fetch))

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

    val deployResult = context.execute(productionPipe.deploy(ingressName = Some("mnist")))
    println(s"Pipeline deployed: ${deployResult.externalUrl}")
  }
}

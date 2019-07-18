package com.example.examples
import java.io.File

import ai.mantik.planner.Context

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

    /*
    // This doesn't work yet, Bug #99
    val productionImageInput = TabularData(
      "image" -> Image(
        28, 28, ListMap(
          ImageChannel.Black -> ImageComponent(FundamentalType.Uint8)
        )
      )
    )
    val inputFilter = AutoAdapt.autoSelectAlgorithm(productionImageInput, trained.functionType.input).force

    val productionPipe = Pipeline.build(
      inputFilter,
      trained
    )

    val deployResult = context.execute(productionPipe.deploy(ingressName = Some("mnist")))
    println(s"Pipeline deployed: ${deployResult.externalUrl}")
    // waiting some time to get online, see bug #95
    Thread.sleep(10000)
     */
  }
}

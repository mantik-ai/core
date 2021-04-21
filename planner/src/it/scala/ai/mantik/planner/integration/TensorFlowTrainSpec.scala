package ai.mantik.planner.integration

import java.nio.file.Paths

class TensorFlowTrainSpec extends IntegrationTestBase with Samples {

  val MnistTrainingPath = Paths.get("bridge/binary/test/mnist_train")
  val MnistTestPath = Paths.get("bridge/binary/test/mnist")
  val TrainingAlgorithmPath = Paths.get("bridge/tf/train/example/mnist_linear")

  it should "train tensorflow" in new EnvWithBridges {
    context.pushLocalMantikItem(MnistTrainingPath)
    context.pushLocalMantikItem(TrainingAlgorithmPath)
    context.pushLocalMantikItem(MnistTestPath)

    // Training
    val trainDataSet = context.loadDataSet("mnist_train")

    val trainAlgorithm = context
      .loadTrainableAlgorithm("mnist_linear")
      .withMetaValue("n_epochs", 5)

    val (trained, stats) = trainAlgorithm.train(trainDataSet)

    // Evaluating
    val testDataSet = context.loadDataSet("mnist_test")
    val adaptedTest = testDataSet.select("select x as image")
    val applied = trained.apply(adaptedTest)

    applied.fetch.run()
  }
}

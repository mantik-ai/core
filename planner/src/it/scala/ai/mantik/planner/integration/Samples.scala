package ai.mantik.planner.integration

import java.nio.file.Paths
import ai.mantik.ds.FundamentalType.Float64
import ai.mantik.ds.Tensor
import ai.mantik.ds.element.{Bundle, TabularBundle, TensorElement}
import ai.mantik.planner.DataSet
import org.scalatest.BeforeAndAfterAll

trait Samples extends BeforeAndAfterAll {
  self: IntegrationTestBase =>

  protected val doubleMultiplyDirectory = Paths.get("bridge/tf/saved_model/test/resources/samples/double_multiply")
  protected val kmeansDirectory = Paths.get("bridge/sklearn/simple_learn/example/kmeans")
  protected val mnistTrainDirectory = Paths.get("bridge/binary/test/mnist_train")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  trait EnvWithBridges {
    context.pushLocalMantikItem(Paths.get("bridge/binary"))
    context.pushLocalMantikItem(Paths.get("bridge/tf/saved_model"))
    context.pushLocalMantikItem(Paths.get("bridge/tf/train"))
    context.pushLocalMantikItem(Paths.get("bridge/sklearn/simple_learn"))
  }

  trait EnvWithAlgorithm extends EnvWithBridges {
    // Note: this will make the appear the algorithm with a new revision each time
    private lazy val doubleMultiplyPushed = context.pushLocalMantikItem(doubleMultiplyDirectory)
    lazy val doubleMultiply = {
      doubleMultiplyPushed;
      context.loadAlgorithm("double_multiply")
    }
  }

  trait EnvWithDataSet extends EnvWithBridges {
    private lazy val mnistTrainPushed = context.pushLocalMantikItem(mnistTrainDirectory)
    lazy val mnistTrain = {
      mnistTrainPushed
      context.loadDataSet("mnist_train")
    }
  }

  trait EnvWithTrainedAlgorithm extends EnvWithBridges {
    context.pushLocalMantikItem(kmeansDirectory)

    private def makeTensor(a: Double, b: Double): TensorElement[Double] = TensorElement(IndexedSeq(a, b))

    val learningData = DataSet.literal(
      TabularBundle.buildColumnWise
        .withColumn(
          "coordinates",
          Tensor(componentType = Float64, shape = List(2)),
          IndexedSeq(
            makeTensor(1, 1),
            makeTensor(2, 2),
            makeTensor(1, 2),
            makeTensor(2, 2),
            makeTensor(3, 3),
            makeTensor(4, 3),
            makeTensor(4, 4)
          )
        )
        .result
    )

    val kmeans = context.loadTrainableAlgorithm("kmeans")

    val (trained, stats) = kmeans.train(learningData)
  }

}

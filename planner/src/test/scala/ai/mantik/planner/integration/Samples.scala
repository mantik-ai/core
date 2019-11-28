package ai.mantik.planner.integration

import java.io.File
import java.nio.file.Paths

import ai.mantik.ds.FundamentalType.Float64
import ai.mantik.ds.{ TabularData, Tensor }
import ai.mantik.ds.element.{ Bundle, TensorElement }
import ai.mantik.planner.DataSet
import org.scalatest.BeforeAndAfterAll

trait Samples extends BeforeAndAfterAll {
  self: IntegrationTestBase =>

  protected val doubleMultiplyDirectory = Paths.get("bridge/tf/saved_model/test/resources/samples/double_multiply")
  protected val kmeansDirectory = Paths.get("bridge/sklearn/simple_learn/example/kmeans")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  trait EnvWithBridges {
    context.pushLocalMantikFile(Paths.get("bridge/binary"))
    context.pushLocalMantikFile(Paths.get("bridge/tf/saved_model"))
    context.pushLocalMantikFile(Paths.get("bridge/tf/train"))
    context.pushLocalMantikFile(Paths.get("bridge/sklearn/simple_learn"))
  }

  trait EnvWithAlgorithm extends EnvWithBridges {
    // Note: this will make the appear the algorithm with a new revision each time
    private lazy val doubleMultiplyPushed = context.pushLocalMantikFile(doubleMultiplyDirectory)
    lazy val doubleMultiply = {
      doubleMultiplyPushed;
      context.loadAlgorithm("double_multiply")
    }
  }

  trait EnvWithTrainedAlgorithm extends EnvWithBridges {
    context.pushLocalMantikFile(kmeansDirectory)

    private def makeTensor(a: Double, b: Double): TensorElement[Double] = TensorElement(IndexedSeq(a, b))

    val learningData = DataSet.literal(
      Bundle.buildColumnWise.withColumn(
        "coordinates", Tensor(componentType = Float64, shape = List(2)),
        IndexedSeq(
          makeTensor(1, 1),
          makeTensor(2, 2),
          makeTensor(1, 2),
          makeTensor(2, 2),
          makeTensor(3, 3),
          makeTensor(4, 3),
          makeTensor(4, 4)
        )
      ).result
    )

    val kmeans = context.loadTrainableAlgorithm("kmeans")

    val (trained, stats) = kmeans.train(learningData)
  }

}

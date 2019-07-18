package ai.mantik.planner.integration

import java.io.File

import ai.mantik.ds.FundamentalType.Float64
import ai.mantik.ds.{ TabularData, Tensor }
import ai.mantik.ds.element.{ Bundle, TensorElement }
import ai.mantik.planner.DataSet
import org.scalatest.BeforeAndAfterAll

trait Samples extends BeforeAndAfterAll {
  self: IntegrationTestBase =>

  private val doubleMultiplyDirectory = new File("bridge/tf/saved_model/test/resources/samples/double_multiply").toPath
  private val kmenasDirectory = new File("bridge/sklearn/simple_learn/example/kmeans").toPath

  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  trait EnvWithAlgorithm {
    // Note: this will make the appear the algorithm with a new revision each time
    private lazy val doubleMultiplyPushed = context.pushLocalMantikFile(doubleMultiplyDirectory)
    lazy val doubleMultiply = {
      doubleMultiplyPushed;
      context.loadAlgorithm("double_multiply")
    }
  }

  trait EnvWithTrainedAlgorithm {
    context.pushLocalMantikFile(kmenasDirectory)

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
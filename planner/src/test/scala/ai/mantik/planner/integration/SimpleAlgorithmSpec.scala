package ai.mantik.planner.integration

import java.io.File

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.planner.DataSet
import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class SimpleAlgorithmSpec extends IntegrationTestBase {

  val sampleFile = new File("bridge/tf/saved_model/test/resources/samples/double_multiply").toPath

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    context.pushLocalMantikFile(sampleFile)
  }

  it should "calculate a transformation" in {
    context.pushLocalMantikFile(sampleFile)

    val dataset = DataSet.literal(
      Bundle.buildColumnWise
        .withPrimitives("x", 1.0, 2.0)
        .result
    )

    val transformation = context.loadAlgorithm("double_multiply")
    val result = context.execute(
      transformation(dataset).fetch
    )

    result shouldBe Bundle.buildColumnWise
      .withPrimitives("y", 2.0, 4.0)
      .result
  }
}

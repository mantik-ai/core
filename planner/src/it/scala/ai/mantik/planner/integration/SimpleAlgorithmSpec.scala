package ai.mantik.planner.integration

import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.planner.DataSet

class SimpleAlgorithmSpec extends IntegrationTestBase with Samples {

  it should "calculate a transformation" in new EnvWithAlgorithm {
    val dataset = DataSet.literal(
      TabularBundle.buildColumnWise
        .withPrimitives("x", 1.0, 2.0)
        .result
    )

    val result = context.execute(
      doubleMultiply(dataset).fetch
    )

    result shouldBe TabularBundle.buildColumnWise
      .withPrimitives("y", 2.0, 4.0)
      .result
  }
}

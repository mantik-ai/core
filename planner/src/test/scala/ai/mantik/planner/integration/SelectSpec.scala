package ai.mantik.planner.integration

import ai.mantik.ds.element.Bundle
import ai.mantik.planner.DataSet
import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class SelectSpec extends IntegrationTestBase {

  it should "be possible to select values" in {
    val input = Bundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3)
      .withPrimitives("y", "a", "b", "c")
      .result

    val dataset = DataSet.literal(input)

    val selected = dataset.select("SELECT (x + 1) as a, y WHERE NOT(x = 2)")
    val result = context.execute(selected.fetch)

    result shouldBe Bundle.buildColumnWise
      .withPrimitives("a", 2, 4)
      .withPrimitives("y", "a", "c")
      .result
  }
}

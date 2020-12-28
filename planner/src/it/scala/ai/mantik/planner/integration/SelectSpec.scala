package ai.mantik.planner.integration

import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.planner.DataSet

class SelectSpec extends IntegrationTestBase {

  it should "be possible to select values" in {
    val input = TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3)
      .withPrimitives("y", "a", "b", "c")
      .result

    val dataset = DataSet.literal(input)

    val selected = dataset.select("SELECT (x + 1) as a, y WHERE NOT(x = 2)")
    val result = context.execute(selected.fetch)

    result shouldBe TabularBundle.buildColumnWise
      .withPrimitives("a", 2, 4)
      .withPrimitives("y", "a", "c")
      .result
  }

  it should "run with chained selects" in {
    val input = TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3)
      .withPrimitives("y", "a", "b", "c")
      .result

    val inputDs = DataSet.literal(input)

    val inputX = inputDs.select("SELECT x as y")
    val inputTwoTimes = inputX.select("SELECT (y * 2) as z")

    val expected = TabularBundle.buildColumnWise
      .withPrimitives("z", 2, 4, 6)
      .result

    val got = inputTwoTimes.fetch.run()
    got shouldBe expected
  }
}

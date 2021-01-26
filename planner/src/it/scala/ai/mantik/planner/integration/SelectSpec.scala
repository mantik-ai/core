package ai.mantik.planner.integration

import ai.mantik.ds.{ArrayT, FundamentalType, Nullable, Struct}
import ai.mantik.ds.element.{ArrayElement, Bundle, NullElement, Primitive, SomeElement, StructElement, TabularBundle}
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

  it should "work with arrays" in {
    val input = TabularBundle.build(
      "x" -> ArrayT(FundamentalType.Int32),
      "idx" -> FundamentalType.Int32
    ).row(ArrayElement(Primitive(10), Primitive(11), Primitive(12), Primitive(13), Primitive(14)), Primitive(2))
      .row(ArrayElement(), Primitive(3))
      .result

    val inputDs = DataSet.literal(input)
    val sqlResult = inputDs.select("SELECT x[idx], size(x)").fetch.run()

    val expected = TabularBundle.build(
      "$1" -> Nullable(FundamentalType.Int32),
      "$2" -> FundamentalType.Int32
    ).row(11, 5)
      .row(NullElement, 0)
      .result

    sqlResult shouldBe expected
  }

  it should "work with sub structures" in {
    val input = TabularBundle.build(
      "person" -> Struct(
        "name" -> FundamentalType.StringType,
        "age" -> Nullable(FundamentalType.Int32)
      )
    ).row(StructElement(Primitive("Alice"), SomeElement(Primitive(42))))
      .row(StructElement(Primitive("Bob"), NullElement))
      .result

    val inputDs = DataSet.literal(input)
    val sqlResult = inputDs.select("SELECT (person).name, (person).age").fetch.run()

    val expected = TabularBundle.build(
      "name" -> FundamentalType.StringType,
      "age" -> Nullable(FundamentalType.Int32)
    ).row("Alice", 42)
      .row("Bob", NullElement)
      .result

    sqlResult shouldBe expected
  }
}

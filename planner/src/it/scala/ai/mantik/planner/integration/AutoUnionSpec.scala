package ai.mantik.planner.integration

import ai.mantik.ds.{FundamentalType, Nullable}
import ai.mantik.ds.element.{Bundle, NullElement, Primitive, SomeElement, TabularBundle}
import ai.mantik.planner.DataSet

class AutoUnionSpec extends IntegrationTestBase {

  trait Env {
    val input1Data = TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3)
      .withPrimitives("y", "a", "b", "c")
      .result

    val input1 = DataSet.literal(input1Data)

    val input2 = DataSet.literal(
      TabularBundle.buildColumnWise
        .withPrimitives("x", 2, 5)
        .withPrimitives("y", "b", "f")
        .result
    )

    val input3 = DataSet.literal(
      TabularBundle.buildColumnWise
        .withPrimitives("x", 4, 3)
        .withPrimitives("z", "foo", "bar")
        .result
    )
  }

  it should "be possible to do a simple autoUnion" in new Env {
    val result = input1.autoUnion(input2, all = false).fetch.run()
    result shouldBe TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3, 5)
      .withPrimitives("y", "a", "b", "c", "f")
      .result
  }

  it should "be possible to do a simple autoUnion ALL" in new Env {
    val result = input1.autoUnion(input2, all = true).fetch.run()
    result shouldBe TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3, 2, 5)
      .withPrimitives("y", "a", "b", "c", "b", "f")
      .result
  }

  it should "be possible to do a simple auto union with type adaption" in new Env {
    val result = input1.autoUnion(input3, all = false).fetch.run()
    result shouldBe TabularBundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3, 4, 3)
      .withColumn(
        "y",
        Nullable(FundamentalType.StringType),
        Vector(
          SomeElement(Primitive("a")),
          SomeElement(Primitive("b")),
          SomeElement(Primitive("c")),
          NullElement,
          NullElement
        )
      )
      .withColumn(
        "z",
        Nullable(FundamentalType.StringType),
        Vector(
          NullElement,
          NullElement,
          NullElement,
          SomeElement(Primitive("foo")),
          SomeElement(Primitive("bar"))
        )
      )
      .result
  }

  it should "be possible to do a self union" in new Env {
    val result = input1.autoUnion(input1, all = false).fetch.run()
    result shouldBe input1Data

    val result2 = input1.autoUnion(input1, all = true).fetch.run()
    result2 shouldBe input1Data.copy(
      rows = input1Data.rows ++ input1Data.rows
    )
  }
}

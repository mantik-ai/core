package ai.mantik.ds.element

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.testutils.TestBase

class ColumnWiseBundleBuilderSpec extends TestBase {

  it should "create nice bundles" in {
    val bundle = ColumnWiseBundleBuilder()
      .withColumn("x", FundamentalType.StringType, Vector(Primitive("a"), Primitive("b")))
      .withPrimitives("y", 1, 2)
      .result

    bundle shouldBe TabularBundle(
      TabularData(
        "x" -> FundamentalType.StringType,
        "y" -> FundamentalType.Int32
      ),
      rows = Vector(
        TabularRow(
          IndexedSeq(Primitive("a"), Primitive(1))
        ),
        TabularRow(
          IndexedSeq(Primitive("b"), Primitive(2))
        )
      )
    )
  }

  it should "detect row count mismatch" in {
    val builder = ColumnWiseBundleBuilder()
      .withPrimitives("x", 1, 2)

    intercept[IllegalArgumentException] {
      builder.withPrimitives("y", 3, 4, 5)
    }
  }
}

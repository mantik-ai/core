package ai.mantik.ds.converter

import ai.mantik.ds.converter.DataTypeConverter.IdentityConverter
import ai.mantik.ds.natural.{ EmbeddedTabularElement, Primitive, TabularRow }
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{ FundamentalType, TabularData }

class DataTypeConverterSpec extends TestBase {

  "IdentityConverter" should "always return the input" in {
    val foo = IdentityConverter(FundamentalType.Int32)
    foo.targetType shouldBe FundamentalType.Int32
    foo.convert(Primitive(3)) shouldBe Primitive(3)
  }

  "ConstantConverter" should "always return constnts" in {
    val c = DataTypeConverter.ConstantConverter(
      FundamentalType.Int32, Primitive(3)
    )
    c.targetType shouldBe FundamentalType.Int32
    c.convert(Primitive(4)) shouldBe Primitive(3)
  }

  "fundamental" should "convert with a function" in {
    val c = DataTypeConverter.fundamental(FundamentalType.Int32) { x: Int => x + 1 }
    c.targetType shouldBe FundamentalType.Int32
    c.convert(Primitive(4)) shouldBe Primitive(5)
  }

  "TabularConverter" should "apply all sub converters" in {
    val t = TabularConverter(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.StringType
      ),
      IndexedSeq(
        DataTypeConverter.fundamental(FundamentalType.Int32) { x: Int => x + 1 },
        DataTypeConverter.ConstantConverter(FundamentalType.StringType, Primitive("boom"))
      )
    )
    val row = TabularRow(
      Primitive(3), Primitive("in")
    )
    val expected = TabularRow(
      Primitive(4), Primitive("boom")
    )

    t.convert(
      EmbeddedTabularElement(Vector(row))
    ) shouldBe EmbeddedTabularElement(Vector(expected))

    t.convert(row) shouldBe expected
  }
}

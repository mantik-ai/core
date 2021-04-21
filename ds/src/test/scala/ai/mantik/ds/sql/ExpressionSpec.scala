package ai.mantik.ds.sql

import ai.mantik.ds.{ArrayT, FundamentalType, Nullable}
import ai.mantik.ds.element.{ArrayElement, NullElement, SingleElementBundle, SomeElement}
import ai.mantik.ds.testutil.TestBase

class ExpressionSpec extends TestBase {

  "ArrayGetExpression" should "have the correct type" in {
    ArrayGetExpression(
      ConstantExpression(SingleElementBundle(ArrayT(FundamentalType.StringType), ArrayElement())),
      ConstantExpression(0)
    ).dataType shouldBe Nullable(FundamentalType.StringType)

    ArrayGetExpression(
      ConstantExpression(SingleElementBundle(Nullable(ArrayT(FundamentalType.StringType)), NullElement)),
      ConstantExpression(0)
    ).dataType shouldBe Nullable(FundamentalType.StringType)
  }

  "SizeExpression" should "have the correct type" in {
    SizeExpression(
      ConstantExpression(SingleElementBundle(ArrayT(FundamentalType.StringType), ArrayElement()))
    ).dataType shouldBe FundamentalType.Int32

    SizeExpression(
      ConstantExpression(SingleElementBundle(Nullable(ArrayT(FundamentalType.StringType)), SomeElement(ArrayElement())))
    ).dataType shouldBe Nullable(FundamentalType.Int32)
  }
}

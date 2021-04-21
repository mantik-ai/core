package ai.mantik.ds.sql.builder

import ai.mantik.ds.{FundamentalType, Nullable}
import ai.mantik.ds.element.{Bundle, SingleElementBundle}
import ai.mantik.ds.sql.{CastExpression, ColumnExpression, ConstantExpression}
import ai.mantik.testutils.TestBase

class CastBuilderSpec extends TestBase {

  "comparisonType" should "return the same type if equals" in {
    CastBuilder.comparisonType(FundamentalType.Int32, FundamentalType.Int32) shouldBe Right(FundamentalType.Int32)
  }

  it should "choose the next higher same type" in {
    CastBuilder.comparisonType(FundamentalType.Int32, FundamentalType.Int64) shouldBe Right(FundamentalType.Int64)
    CastBuilder.comparisonType(FundamentalType.Int64, FundamentalType.Int32) shouldBe Right(FundamentalType.Int64)
    CastBuilder.comparisonType(FundamentalType.Float32, FundamentalType.Float64) shouldBe Right(FundamentalType.Float64)
    CastBuilder.comparisonType(FundamentalType.Float64, FundamentalType.Float32) shouldBe Right(FundamentalType.Float64)
  }

  it should "figure out matching floating points" in {
    CastBuilder.comparisonType(FundamentalType.Int8, FundamentalType.Float32) shouldBe Right(FundamentalType.Float32)
    CastBuilder.comparisonType(FundamentalType.Int32, FundamentalType.Float32) shouldBe Right(FundamentalType.Float64)
    CastBuilder.comparisonType(FundamentalType.Int64, FundamentalType.Float64) shouldBe 'left
  }

  it should "handle nullable values" in {
    CastBuilder.comparisonType(Nullable(FundamentalType.Int32), FundamentalType.Int8) shouldBe Right(
      Nullable(FundamentalType.Int32)
    )
    CastBuilder.comparisonType(FundamentalType.Int32, Nullable(FundamentalType.Int8)) shouldBe Right(
      Nullable(FundamentalType.Int32)
    )
  }

  "wrapType" should "wrap a type in a cast" in {
    CastBuilder.wrapType(ColumnExpression(1, FundamentalType.Int32), FundamentalType.Float64) shouldBe
      Right(CastExpression(ColumnExpression(1, FundamentalType.Int32), FundamentalType.Float64))
  }

  it should "do nothing if the type is ok" in {
    CastBuilder.wrapType(ColumnExpression(1, FundamentalType.Int32), FundamentalType.Int32) shouldBe
      Right(ColumnExpression(1, FundamentalType.Int32))
  }

  it should "auto convert constants" in {
    CastBuilder.wrapType(
      ConstantExpression(Bundle.fundamental(100: Byte)),
      FundamentalType.Int32
    ) shouldBe Right(ConstantExpression(Bundle.fundamental(100)))
  }

  it should "fail for impossible casts" in {
    CastBuilder.wrapType(
      ColumnExpression(1, FundamentalType.VoidType),
      FundamentalType.Int32
    ) shouldBe 'left
  }
}

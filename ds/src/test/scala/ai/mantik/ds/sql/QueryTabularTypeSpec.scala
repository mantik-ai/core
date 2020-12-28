package ai.mantik.ds.sql

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.FundamentalType
import ai.mantik.testutils.TestBase

class QueryTabularTypeSpec extends TestBase {

  "shadow" should "work" in {
    val input = QueryTabularType(
      "a" -> FundamentalType.Int32,
      "b" -> FundamentalType.Int32,
      "a" -> FundamentalType.Int32,
      "c" -> FundamentalType.StringType,
      "a" -> FundamentalType.BoolType
    )
    input.shadow() shouldBe QueryTabularType(
      "a" -> FundamentalType.Int32,
      "b" -> FundamentalType.Int32,
      "a0" -> FundamentalType.Int32,
      "c" -> FundamentalType.StringType,
      "a1" -> FundamentalType.BoolType
    )
    input.shadow(fromLeft = false) shouldBe QueryTabularType(
      "a1" -> FundamentalType.Int32,
      "b" -> FundamentalType.Int32,
      "a0" -> FundamentalType.Int32,
      "c" -> FundamentalType.StringType,
      "a" -> FundamentalType.BoolType
    )
  }

  it should "bail out if it is too complex" in {
    intercept[FeatureNotSupported] {
      QueryTabularType(
        { for (i <- 0 until 300) yield ("a" -> FundamentalType.Int32) }: _*
      ).shadow()
    }
  }

  it should "respect ignoreAlias" in {
    val sample = QueryTabularType(
      "x" -> FundamentalType.Int32,
      "a.x" -> FundamentalType.Int32,
      "b.x" -> FundamentalType.Int32
    )
    sample.shadow() shouldBe sample
    sample.shadow(ignoreAlias = true) shouldBe QueryTabularType(
      "x" -> FundamentalType.Int32,
      "a.x0" -> FundamentalType.Int32,
      "b.x1" -> FundamentalType.Int32
    )
  }
}

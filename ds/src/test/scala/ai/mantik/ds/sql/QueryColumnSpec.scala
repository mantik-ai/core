package ai.mantik.ds.sql

import ai.mantik.ds.FundamentalType
import ai.mantik.testutils.TestBase

class QueryColumnSpec extends TestBase {

  "apply" should "work" in {
    QueryColumn("abc", FundamentalType.Int32) shouldBe QueryColumn("abc", None, FundamentalType.Int32)
    QueryColumn("alias.abc", FundamentalType.Int32) shouldBe QueryColumn("abc", Some("alias"), FundamentalType.Int32)
  }
}

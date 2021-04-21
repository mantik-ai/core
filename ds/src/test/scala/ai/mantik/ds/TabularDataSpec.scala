package ai.mantik.ds

import ai.mantik.ds.testutil.TestBase

import scala.collection.immutable.ListMap

class TabularDataSpec extends TestBase {

  it should "be build able from name values" in {
    TabularData(
      "foo" -> FundamentalType.Int8,
      "bar" -> FundamentalType.BoolType
    ) shouldBe TabularData(
      columns = ListMap(
        "foo" -> FundamentalType.Int8,
        "bar" -> FundamentalType.BoolType
      ),
      rowCount = None
    )
  }

  it should "know it's order" in {
    val tab = TabularData(
      "foo" -> FundamentalType.Int8,
      "bar" -> FundamentalType.BoolType,
      "buz" -> FundamentalType.StringType
    )
    tab.lookupColumnIndex("foo") shouldBe Some(0)
    tab.lookupColumnIndex("bar") shouldBe Some(1)
    tab.lookupColumnIndex("buz") shouldBe Some(2)
    tab.lookupColumnIndex("unknown") shouldBe None
  }
}

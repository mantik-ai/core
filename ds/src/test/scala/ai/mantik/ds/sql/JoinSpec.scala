package ai.mantik.ds.sql

import ai.mantik.ds.sql.JoinCondition.UsingColumn
import ai.mantik.ds.{ FundamentalType, Nullable, TabularData }
import ai.mantik.testutils.TestBase

class JoinSpec extends TestBase {

  "resultingTabularType" should "work" in {
    val left = AnonymousInput(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.StringType
      )
    )

    val right = AnonymousInput(
      TabularData(
        "x" -> FundamentalType.Int64,
        "z" -> Nullable(FundamentalType.StringType)
      )
    )

    val join1 = Join(
      left, right, JoinType.Left, JoinCondition.Cross
    )

    // More Tests can be foujnd in JoinBuilder.innerTabularData

    join1.resultingTabularType shouldBe TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.StringType,
      "x0" -> Nullable(FundamentalType.Int64),
      "z" -> Nullable(FundamentalType.StringType)
    )

    val join2 = Join(
      left, right, JoinType.Left, JoinCondition.Using(Vector(UsingColumn("x", false, 0, 0, 2, FundamentalType.Int32)))
    )

    join2.resultingTabularType shouldBe TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.StringType,
      "z" -> Nullable(FundamentalType.StringType)
    )
  }
}

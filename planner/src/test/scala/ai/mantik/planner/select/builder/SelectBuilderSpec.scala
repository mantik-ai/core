package ai.mantik.planner.select.builder

import ai.mantik.ds.element.{Bundle, Primitive}
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.planner.select._
import ai.mantik.testutils.TestBase

class SelectBuilderSpec extends TestBase {

  val simpleInput = TabularData(
    "x" -> FundamentalType.Int32,
    "y" -> FundamentalType.StringType
  )

  val emptyInput = TabularData()

  it should "support select *" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT *")
    got shouldBe Right(
      Select(
        List(
          SelectProjection("x", ColumnExpression(0, FundamentalType.Int32)),
          SelectProjection("y", ColumnExpression(1, FundamentalType.StringType))
        )
      )
    )
    got.right.get.resultingType shouldBe simpleInput
  }

  it should "support selecting a single" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT y")
    got shouldBe Right(
      Select(
        List(
          SelectProjection("y", ColumnExpression(1, FundamentalType.StringType))
        )
      )
    )
    got.right.get.resultingType shouldBe TabularData(
      "y" -> FundamentalType.StringType
    )
  }

  it should "support selecting multiple" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT y,x")
    got shouldBe Right(
      Select(
        List(
          SelectProjection("y", ColumnExpression(1, FundamentalType.StringType)),
          SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
        )
      )
    )
    got.right.get.resultingType shouldBe TabularData(
      "y" -> FundamentalType.StringType,
      "x" -> FundamentalType.Int32
    )
  }

  it should "support simple casts" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT CAST(x as int64)")
    got shouldBe Right(
      Select(
        List(
          SelectProjection(
            "x",
            CastExpression(
              ColumnExpression(0, FundamentalType.Int32),
              FundamentalType.Int64
            )
          )
        )
      )
    )
    got.right.get.resultingType shouldBe TabularData(
      "x" -> FundamentalType.Int64
    )
  }

  it should "support simple constants" in {
    val got = SelectBuilder.buildSelect(emptyInput, "SELECT 1,true,false,2.5,void")
    val expected = Select(
      List(
        SelectProjection("$1", ConstantExpression(Bundle.build(FundamentalType.Int8, Primitive(1: Byte)))),
        SelectProjection("$2", ConstantExpression(Bundle.build(FundamentalType.BoolType, Primitive(true)))),
        SelectProjection("$3", ConstantExpression(Bundle.build(FundamentalType.BoolType, Primitive(false)))),
        SelectProjection("$4", ConstantExpression(Bundle.build(FundamentalType.Float32, Primitive(2.5f)))),
        SelectProjection("$5", ConstantExpression(Bundle.build(FundamentalType.VoidType, Primitive.unit)))
      )
    )
    got.right.get.projections.zip(expected.projections).foreach {
      case (a, b) =>
        a shouldBe b
    }
    got shouldBe Right(
      expected
    )
  }

  it should "support simple filters" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT x WHERE x = 5")
    val expected = Select(
      List(
        SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
      ),
      List(
        Condition.Equals(
          ColumnExpression(0, FundamentalType.Int32),
          // TODO: It should optimize away this cast
          CastExpression(
            ConstantExpression(Bundle.fundamental(5.toByte)),
            FundamentalType.Int32,
          )
        )
      )
    )
    got shouldBe Right(expected)
  }

  it should "support simple filters II" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT x WHERE y = 'Hello World'")
    val expected = Select(
      List(
        SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
      ),
      List(
        Condition.Equals(
          ColumnExpression(1, FundamentalType.StringType),
          ConstantExpression(Bundle.fundamental("Hello World"))
        )
      )
    )
    got shouldBe Right(expected)
  }

  it should "support simple combined filters" in {
    val got = SelectBuilder.buildSelect(simpleInput, "SELECT x WHERE y = 'Hello World' and x = 1")
    val expected = Select(
      List(
        SelectProjection("x", ColumnExpression(0, FundamentalType.Int32))
      ),
      List(
        Condition.Equals(
          ColumnExpression(1, FundamentalType.StringType),
          ConstantExpression(Bundle.fundamental("Hello World"))
        ),
        Condition.Equals(
          ColumnExpression(0, FundamentalType.Int32),
          // TODO: It should optimize away this cast
          CastExpression(
            ConstantExpression(Bundle.fundamental(1.toByte)),
            FundamentalType.Int32,
          )
        )
      )
    )
    got shouldBe Right(expected)
  }
}

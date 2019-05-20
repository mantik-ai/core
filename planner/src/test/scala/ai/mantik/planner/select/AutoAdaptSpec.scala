package ai.mantik.planner.select

import ai.mantik.ds.{DataType, FundamentalType, TabularData}
import ai.mantik.planner.{DataSet, Source}
import ai.mantik.repository.{DataSetDefinition, Mantikfile}
import ai.mantik.testutils.TestBase

class AutoAdaptSpec extends TestBase {

  val type1 = TabularData (
    "x" -> FundamentalType.Int32
  )

  val type2 = TabularData(
    "y" -> FundamentalType.Int32
  )



  private def makeDs(dt: DataType): DataSet = {
    // DataSet source is not important here.
    DataSet(
      Source.Loaded("someId"),
      Mantikfile.pure(DataSetDefinition(format = "someformat", `type` = dt))
    )
  }

  "autoAdapt" should "not touch identical datasets" in {
    val ds1 = makeDs(type1)
    AutoAdapt.autoAdapt(ds1, type1) shouldBe Right(ds1)
  }

  it should "figure out figure out single columns" in {
    val ds1 = makeDs(type1)
    val adapted = AutoAdapt.autoAdapt(ds1, type2).right.getOrElse(fail)
    adapted.dataType shouldBe type2
    adapted.source shouldBe an[Source.OperationResult]
  }

  "autoSelect" should "select single renamings" in {
    val s = AutoAdapt.autoSelect(type1, type2).right.getOrElse(fail)
    s.inputType shouldBe type1
    s.resultingType shouldBe type2
    s.selection shouldBe empty
    s.projections shouldBe Some(
      List(SelectProjection("y", ColumnExpression(0, FundamentalType.Int32)))
    )
  }

  it should "work for switched order" in {
    val type3 = TabularData(
      "a" -> FundamentalType.StringType,
      "u" -> FundamentalType.BoolType, // unreferenced
      "b" -> FundamentalType.Int32,
    )

    val type4 = TabularData(
      "b" -> FundamentalType.Int32,
      "a" -> FundamentalType.StringType
    )
    val s = AutoAdapt.autoSelect(type3, type4).right.getOrElse(fail)
    s.inputType shouldBe type3
    s.selection shouldBe empty
    s.resultingType shouldBe type4
    s.projections shouldBe Some(
      List(
        SelectProjection("b", ColumnExpression(2, FundamentalType.Int32)),
        SelectProjection("a", ColumnExpression(0, FundamentalType.StringType))
      )
    )
  }

  it should "for the empty left-over" in {
    val type3 = TabularData(
      "a" -> FundamentalType.StringType,
      "z" -> FundamentalType.Int32
    )

    val type4 = TabularData(
      "b" -> FundamentalType.Int32,
      "a" -> FundamentalType.StringType
    )
    val s = AutoAdapt.autoSelect(type3, type4).right.getOrElse(fail)
    s.inputType shouldBe type3
    s.selection shouldBe empty
    s.resultingType shouldBe type4
    s.projections shouldBe Some(
      List(
        SelectProjection("b", ColumnExpression(1, FundamentalType.Int32)),
        SelectProjection("a", ColumnExpression(0, FundamentalType.StringType))
      )
    )
  }

  it should "cast incompatible datatypes" in {
    val type3 = TabularData(
      "a" -> FundamentalType.Int32
    )

    val type4 = TabularData(
      "a" -> FundamentalType.Int64
    )
    val s = AutoAdapt.autoSelect(type3, type4).right.getOrElse(fail)
    s.inputType shouldBe type3
    s.selection shouldBe empty
    s.resultingType shouldBe type4
    s.projections shouldBe Some(
      List(
        SelectProjection("a", CastExpression(ColumnExpression(0, FundamentalType.Int32), FundamentalType.Int64))
      )
    )
  }

  it should "not cast if the cast would loose precision" in {
    val type3 = TabularData(
      "a" -> FundamentalType.Int32
    )

    val type4 = TabularData(
      "a" -> FundamentalType.Int8
    )
    val s = AutoAdapt.autoSelect(type3, type4).left.getOrElse(fail)
    s should include("loose")
  }

  it should "not cast if the cast could fail" in {
    val type3 = TabularData(
      "a" -> FundamentalType.StringType
    )

    val type4 = TabularData(
      "a" -> FundamentalType.Int32
    )
    val s = AutoAdapt.autoSelect(type3, type4).left.getOrElse(fail)
    s should include("fail")
  }

}

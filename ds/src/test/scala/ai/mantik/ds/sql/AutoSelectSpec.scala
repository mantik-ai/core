package ai.mantik.ds.sql

import ai.mantik.ds.FundamentalType.{Float32, Float64, Uint8}
import ai.mantik.ds._
import ai.mantik.testutils.TestBase

class AutoSelectSpec extends TestBase {

  val type1 = TabularData(
    "x" -> FundamentalType.Int32
  )

  val type2 = TabularData(
    "y" -> FundamentalType.Int32
  )

  "autoSelect" should "select single renamings" in {
    val s = AutoSelect.autoSelect(type1, type2).right.getOrElse(fail)
    s.inputTabularType shouldBe type1
    s.resultingTabularType shouldBe type2
    s.selection shouldBe empty
    s.projections shouldBe Some(
      List(SelectProjection("y", ColumnExpression(0, FundamentalType.Int32)))
    )
  }

  it should "work for switched order" in {
    val type3 = TabularData(
      "a" -> FundamentalType.StringType,
      "u" -> FundamentalType.BoolType, // unreferenced
      "b" -> FundamentalType.Int32
    )

    val type4 = TabularData(
      "b" -> FundamentalType.Int32,
      "a" -> FundamentalType.StringType
    )
    val s = AutoSelect.autoSelect(type3, type4).right.getOrElse(fail)
    s.inputTabularType shouldBe type3
    s.selection shouldBe empty
    s.resultingTabularType shouldBe type4
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
    val s = AutoSelect.autoSelect(type3, type4).right.getOrElse(fail)
    s.inputTabularType shouldBe type3
    s.selection shouldBe empty
    s.resultingTabularType shouldBe type4
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
    val s = AutoSelect.autoSelect(type3, type4).right.getOrElse(fail)
    s.inputTabularType shouldBe type3
    s.selection shouldBe empty
    s.resultingTabularType shouldBe type4
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
    val s = AutoSelect.autoSelect(type3, type4).left.getOrElse(fail)
    s should include("loose")
  }

  it should "not cast if the cast could fail" in {
    val type3 = TabularData(
      "a" -> FundamentalType.StringType
    )

    val type4 = TabularData(
      "a" -> FundamentalType.Int32
    )
    val s = AutoSelect.autoSelect(type3, type4).left.getOrElse(fail)
    s should include("fail")
  }

  it should "convert an image to tensor while changing types" in {
    val type3 = TabularData(
      "a" -> Image.plain(28, 28, ImageChannel.Black -> Uint8)
    )
    val type4 = TabularData(
      "a" -> Tensor(componentType = Float32, shape = List(28, 28))
    )
    // Note: this conversions is not easily writeable with SQL
    val s = AutoSelect.autoSelect(type3, type4).forceRight
    s.resultingTabularType shouldBe type4

    val selectStatement = s.toStatement
    withClue(s"Parsing select statement ${selectStatement} should yield same type.") {
      val parsedAgain = Select.parse(type3, s.toStatement).forceRight
      parsedAgain.resultingTabularType shouldBe type4
    }
  }

  it should "work for a tensor to image change" in {
    val type3 = TabularData(
      "a" -> Tensor(componentType = Float32, shape = List(28, 28))
    )
    val type4 = TabularData(
      "a" -> Image.plain(28, 28, ImageChannel.Red -> Float64)
    )
    // Note: this conversions is not easily writeable with SQL
    val s = AutoSelect.autoSelect(type3, type4).forceRight
    s.resultingTabularType shouldBe type4

    val selectStatement = s.toStatement
    withClue(s"Parsing select statement ${selectStatement} should yield same type.") {
      val parsedAgain = Select.parse(type3, s.toStatement).forceRight
      parsedAgain.resultingTabularType shouldBe type4
    }
  }

}

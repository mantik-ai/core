package ai.mantik.ds.sql

import ai.mantik.ds.element.{ Bundle, NullElement, TensorElement }
import ai.mantik.ds.sql.run.{ Compiler, SelectProgram }
import ai.mantik.ds.{ FundamentalType, Nullable, TabularData, Tensor }
import ai.mantik.testutils.TestBase

class SelectSpec extends TestBase {

  val simpleBundle = Bundle.build(
    TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.Int32,
      "z" -> FundamentalType.StringType
    )
  ).row(1, 2, "Hello")
    .row(2, 3, "World")
    .row(3, 2, "!!!!")
    .result

  it should "run simple selects" in {
    Select.run(simpleBundle, "select x") shouldBe Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32
      )
    ).row(1).row(2).row(3).result
  }

  it should "run simple filters" in {
    Select.run(simpleBundle, "select * where y = 2") shouldBe Bundle.build(
      simpleBundle.model.asInstanceOf[TabularData]
    ).row(1, 2, "Hello").row(3, 2, "!!!!").result

    Select.run(simpleBundle, "select * where y = 5").rows.isEmpty shouldBe true
  }

  it should "run complex filters with and" in {
    Select.run(simpleBundle, "select z where y = 2 and x = 3 and z = '!!!!'") shouldBe Bundle.build(
      TabularData(
        "z" -> FundamentalType.StringType
      )
    ).row("!!!!").result
  }

  it should "run complex filters with or" in {
    Select.run(simpleBundle, "select z where y = 2 or x = 3") shouldBe Bundle.build(
      TabularData(
        "z" -> FundamentalType.StringType
      )
    ).row("Hello").row("!!!!").result
  }

  it should "run complex filters with not" in {
    Select.run(simpleBundle, "select z where not(y = 2)") shouldBe Bundle.build(
      TabularData(
        "z" -> FundamentalType.StringType
      )
    ).row("World").result
  }

  it should "run complex filters with not2" in {
    Select.run(simpleBundle, "select z where not(x = 1 or x = 2)") shouldBe Bundle.build(
      TabularData(
        "z" -> FundamentalType.StringType
      )
    ).row("!!!!").result
  }

  it should "run simple calculations" in {
    Select.run(simpleBundle, "select x + y as i") shouldBe Bundle.build(
      TabularData(
        "i" -> FundamentalType.Int32
      )
    ).row(3).row(5).row(5).result

    Select.run(simpleBundle, "select x + y, z") shouldBe Bundle.build(
      TabularData(
        "$1" -> FundamentalType.Int32,
        "z" -> FundamentalType.StringType
      )
    ).row(3, "Hello").row(5, "World").row(5, "!!!!").result
  }

  it should "run simple casts" in {
    Select.run(simpleBundle, "select CAST(x as float64)") shouldBe Bundle.build(
      TabularData(
        "x" -> FundamentalType.Float64
      )
    ).row(1.0).row(2.0).row(3.0).result
  }

  it should "run tensor casts" in {
    Select.run(simpleBundle, "select CAST(x as tensor) where x = 1") shouldBe Bundle.build(
      TabularData(
        "x" -> Tensor(FundamentalType.Int32, List(1))
      )
    ).row(TensorElement(IndexedSeq(1))).result
  }

  it should "run constants" in {
    Select.run(simpleBundle, "select 1") shouldBe Bundle.build(
      TabularData(
        "$1" -> FundamentalType.Int8
      )
    ).row(1.toByte).row(1.toByte).row(1.toByte).result
  }

  val bundleWithNulls = Bundle.build(
    TabularData(
      "x" -> Nullable(FundamentalType.Int32),
      "y" -> FundamentalType.StringType
    )
  ).row(1, "Hello")
    .row(NullElement, "World")
    .result

  it should "allow filtering out null values" in {
    Select.run(
      bundleWithNulls, "select * where x is not null"
    ) shouldBe Bundle.build(bundleWithNulls.model).row(1, "Hello").result
  }

  it should "allow casting to nullable" in {
    Select.run(
      bundleWithNulls, "SELECT x, CAST(y as STRING NULLABLE) as y"
    ) shouldBe Bundle.build(
      TabularData(
        "x" -> Nullable(FundamentalType.Int32),
        "y" -> Nullable(FundamentalType.StringType)
      )
    ).row(1, "Hello")
      .row(NullElement, "World")
      .result
  }

  it should "allow casting from nullable" in {
    Select.run(
      bundleWithNulls, "SELECT CAST(x as int32) as x WHERE x IS NOT NULL"
    ) shouldBe Bundle.build(
      TabularData(
        "x" -> FundamentalType.Int32
      )
    ).row(1).result
  }
}

package ai.mantik.planner.select

import ai.mantik.ds.{ FundamentalType, TabularData, Tensor }
import ai.mantik.ds.element.{ Bundle, TensorElement }
import ai.mantik.planner.repository.Bridge
import ai.mantik.planner.select.run.SelectProgram
import ai.mantik.testutils.TestBase
import ai.mantik.planner.select.run.Compiler
import io.circe.syntax._

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

  it should "create valid mantikHeaders" in {
    val select = Select.parse(simpleBundle.model, "select x where x = 1").getOrElse(fail)
    val mantikHeader = select.compileToSelectMantikHeader().getOrElse(fail)
    mantikHeader.definition.bridge shouldBe Bridge.selectBridge.mantikId
    val program = Compiler.compile(select).right.getOrElse(fail)
    mantikHeader.toJsonValue.asObject.get("selectProgram").get.as[SelectProgram] shouldBe Right(program)
  }
}

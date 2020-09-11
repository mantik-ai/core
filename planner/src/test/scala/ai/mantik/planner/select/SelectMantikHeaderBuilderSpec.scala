package ai.mantik.planner.select

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.sql.Select
import ai.mantik.ds.sql.run.{ Compiler, SelectProgram }
import ai.mantik.planner.repository.Bridge
import ai.mantik.testutils.TestBase

class SelectMantikHeaderBuilderSpec extends TestBase {
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

  it should "create valid mantikHeaders" in {
    val select = Select.parse(simpleBundle.model, "select x where x = 1").getOrElse(fail)
    val mantikHeader = SelectMantikHeaderBuilder.compileSelectToMantikHeader(select).forceRight
    mantikHeader.definition.bridge shouldBe Bridge.selectBridge.mantikId
    val program = Compiler.compile(select).right.getOrElse(fail)
    mantikHeader.toJsonValue.asObject.get("selectProgram").get.as[SelectProgram] shouldBe Right(program)
  }
}

package ai.mantik.planner.select

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.{ Bundle, TabularBundle }
import ai.mantik.ds.sql.{ Query, Select, SqlContext }
import ai.mantik.ds.sql.run.{ Compiler, SelectProgram, TableGeneratorProgram }
import ai.mantik.planner.repository.Bridge
import ai.mantik.testutils.TestBase

class SelectMantikHeaderBuilderSpec extends TestBase {
  val simpleBundle = TabularBundle.build(
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
    val select = Select.parse(simpleBundle.model, "select x where x = 1").forceRight
    val mantikHeader = SelectMantikHeaderBuilder.compileToMantikHeader(select).forceRight
    mantikHeader.definition.bridge shouldBe Bridge.selectBridge.mantikId
    val program = Compiler.compile(select).right.getOrElse(fail)
    mantikHeader.toJsonValue.asObject.get("program").get.as[TableGeneratorProgram] shouldBe Right(program)
  }

  it should "work for joins" in {
    implicit val context = SqlContext.apply(
      Vector(
        simpleBundle.model,
        simpleBundle.model
      )
    )
    val join = Query.parse("select l.x, r.z from $0 AS l JOIN $1 AS r ON l.x = r.x").forceRight
    val mantikHeader = SelectMantikHeaderBuilder.compileToMantikHeader(join).forceRight
    mantikHeader.definition.bridge shouldBe Bridge.selectBridge.mantikId
    val program = Compiler.compile(join).right.getOrElse(fail)
    mantikHeader.toJsonValue.asObject.get("program").get.as[TableGeneratorProgram] shouldBe Right(program)
  }
}

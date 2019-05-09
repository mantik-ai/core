package ai.mantik.planner.select.run

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.planner.select.Select
import ai.mantik.testutils.TestBase
import io.circe.syntax._

class SelectProgramSpec extends TestBase {

  it should "be serializable" in {
    val sampleData = TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.Int32,
      "z" -> FundamentalType.StringType
    )
    val select = Select.parse(sampleData, "select x where y = 1").getOrElse(fail)
    val program = Compiler.compile(select).getOrElse(fail)
    program.asJson.as[SelectProgram] shouldBe Right(program)
  }
}

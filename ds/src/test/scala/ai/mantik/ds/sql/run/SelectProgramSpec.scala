package ai.mantik.ds.sql.run

import ai.mantik.ds.sql.Select
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{FundamentalType, TabularData}
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
    (program: TableGeneratorProgram).asJson.as[TableGeneratorProgram] shouldBe Right(program)
  }
}

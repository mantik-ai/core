package ai.mantik.ds.funcational

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.ds.testutil.TestBase
import io.circe.Json
import io.circe.syntax._

class FunctionTypeSpec extends TestBase {

  it should "parse well" in {
    val sample =
      """
        |{
        | "input": "uint8",
        | "output": "string"
        |}
      """.stripMargin
    val jsonParsed = CirceJson.forceParseJson(sample)
    val parsed = jsonParsed.as[FunctionType].getOrElse(fail())
    parsed shouldBe SimpleFunction(
      FundamentalType.Uint8,
      FundamentalType.StringType
    )
    parsed.asJson.as[FunctionType].getOrElse(fail()) shouldBe parsed
  }
}

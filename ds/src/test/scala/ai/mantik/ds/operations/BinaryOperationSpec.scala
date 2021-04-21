package ai.mantik.ds.operations

import ai.mantik.testutils.TestBase
import io.circe.Json
import io.circe.syntax._

class BinaryOperationSpec extends TestBase {

  it should "translate well to JSON" in {
    (BinaryOperation.Mul: BinaryOperation).asJson shouldBe Json.fromString("mul")
    Json.fromString("mul").as[BinaryOperation] shouldBe Right(BinaryOperation.Mul)
    for {
      x <- Seq
        .apply[BinaryOperation](BinaryOperation.Add, BinaryOperation.Sub, BinaryOperation.Mul, BinaryOperation.Div)
    } {
      x.asJson.as[BinaryOperation] shouldBe Right(x)
    }
  }
}

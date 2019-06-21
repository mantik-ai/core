package ai.mantik.elements.meta

import ai.mantik.ds.element.Bundle
import ai.mantik.testutils.TestBase
import io.circe.Json
import io.circe.syntax._

class MetaVariableSpec extends TestBase {

  it should "serialize fine" in {
    val m = MetaVariable(
      "foo",
      Bundle.fundamental(100),
      fix = true
    )
    m.asJson shouldBe Json.obj(
      "name" -> Json.fromString("foo"),
      "value" -> Json.fromInt(100),
      "type" -> Json.fromString("int32"),
      "fix" -> Json.fromBoolean(true)
    )

    m.asJson.as[MetaVariable] shouldBe Right(m)
  }
}

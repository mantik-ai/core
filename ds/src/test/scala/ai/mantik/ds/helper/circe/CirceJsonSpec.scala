package ai.mantik.ds.helper.circe

import ai.mantik.ds.testutil.TestBase
import io.circe.Json
import io.circe.syntax._

class CirceJsonSpec extends TestBase {

  "stripNullValues" should "work" in {
    val x = Json.obj(
      "a" -> true.asJson,
      "b" -> "Hello".asJson,
      "c" -> Json.obj(
        "foo" -> Json.Null,
        "bar" -> Json.arr(
          Json.Null, Json.obj(
            "x" -> Json.Null,
            "y" -> 3.asJson,
            "z" -> "35".asJson
          )
        )
      )
    )
    CirceJson.stripNullValues(
      x
    ) shouldBe Json.obj(
      "a" -> true.asJson,
      "b" -> "Hello".asJson,
      "c" -> Json.obj(
        "bar" -> Json.arr(
          Json.Null, Json.obj(
            "y" -> 3.asJson,
            "z" -> "35".asJson
          )
        )
      )
    )
  }
}

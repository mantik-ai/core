package ai.mantik.ds.helper.circe

import ai.mantik.ds.helper.circe.EnumDiscriminatorCodec.UnregisteredElementException
import ai.mantik.ds.testutil.TestBase
import io.circe.Json
import io.circe.syntax._

class EnumDiscriminatorCodecSpec extends TestBase {

  sealed trait Example
  case object Blue extends Example
  case object Red extends Example
  case object Green extends Example

  case object BadUnregeristered extends Example

  implicit object codec
      extends EnumDiscriminatorCodec[Example](
        Seq(
          "blue" -> Blue,
          "red" -> Red,
          "green" -> Green
        )
      )

  it should "decode and encode them all" in {
    for (x <- Seq(Blue, Red, Green)) {
      (x: Example).asJson.as[Example].right.get shouldBe x
    }
  }

  it should "encode them nice" in {
    (Blue: Example).asJson shouldBe Json.fromString("blue")
  }

  it should "handle encoding errors" in {
    intercept[UnregisteredElementException] {
      (BadUnregeristered: Example).asJson
    }
  }

  it should "handle decoding errors" in {
    Json.fromString("unknown").as[Example] shouldBe 'left
    Json.fromBoolean(true).as[Example] shouldBe 'left
  }

  it should "provide simple serializers" in {
    for (x <- Seq(Blue, Red, Green)) {
      codec.stringToElement(codec.elementToString(x)) shouldBe Some(x)
    }
  }
}

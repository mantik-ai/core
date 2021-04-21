package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.testutils.TestBase
import io.circe.{Decoder, Encoder}
import io.circe.syntax._

class ActionSpec extends TestBase {

  it should "be serializable" in {
    val ds = DataSet.literal(Bundle.fundamental(5))
    for {
      action <- Seq(
        ds.push(),
        ds.save(),
        ds.fetch,
        Action.Deploy(ds, Some("foo"), Some("bar"))
      )
    } {
      val serialized = (action: Action[_]).asJson
      val back = serialized.as[Action[_]]
      back shouldBe Right(action)
    }
  }

  it should "serialize responses" in {
    def test[T](value: T)(implicit codec: Encoder[T], decoder: Decoder[T]): Unit = {
      value.asJson.as[T] shouldBe Right(value)
    }
    test((): Unit)
    test(Bundle.fundamental(10): Bundle)
    test(DeploymentState("foo", "mnp://intern1", Some("http://external1")))
  }
}

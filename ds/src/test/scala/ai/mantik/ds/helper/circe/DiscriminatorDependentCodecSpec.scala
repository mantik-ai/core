package ai.mantik.ds.helper.circe

import ai.mantik.ds.testutil.TestBase
import io.circe._
import io.circe.syntax._

class DiscriminatorDependentCodecSpec extends TestBase {

  trait Base
  case class Foo(name: String) extends Base
  case class Bar(age: Int) extends Base

  object Base extends DiscriminatorDependentCodec[Base] {
    override val subTypes = Seq(
      makeSubType[Foo]("foo", isDefault = true),
      makeSubType[Bar]("bar")
    )
  }

  val foo = Foo("Alice"): Base
  val bar = Bar(42): Base

  it should "encode and decode" in {
    foo.asJson.as[Base] shouldBe Right(foo)
    bar.asJson.as[Base] shouldBe Right(bar)
  }

  it should "add the kind" in {
    foo.asJson shouldBe Json.obj(
      "name" -> Json.fromString("Alice"),
      "kind" -> Json.fromString("foo")
    )
    bar.asJson shouldBe Json.obj(
      "age" -> Json.fromInt(42),
      "kind" -> Json.fromString("bar")
    )
  }

  it should "decode default values" in {
    Json.obj(
      "name" -> Json.fromString("bum")
    ).as[Base] shouldBe Right(Foo("bum"))
  }

}

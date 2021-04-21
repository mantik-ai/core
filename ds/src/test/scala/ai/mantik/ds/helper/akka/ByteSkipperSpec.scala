package ai.mantik.ds.helper.akka

import ai.mantik.ds.testutil.{GlobalAkkaSupport, TestBase}
import akka.stream.scaladsl.Source
import akka.util.ByteString

class ByteSkipperSpec extends TestBase with GlobalAkkaSupport {

  trait Env {
    val skipper = ByteSkipper.make(2)
  }

  it should "do nothing if there there is no input" in new Env {
    val input = Source(
      Vector()
    )
    collectSource(input.via(skipper)) shouldBe empty
  }

  it should "do nothing if there are not enough bytes" in new Env {
    val input = Source(
      Vector(ByteString(1))
    )
    collectSource(input.via(skipper)) shouldBe empty
  }

  it should "skip the first bytes" in new Env {
    val input = Source(
      Vector(
        ByteString(1),
        ByteString(2, 3, 4),
        ByteString(5, 6)
      )
    )
    collectSource(input.via(skipper)) shouldBe Seq(
      ByteString(3, 4),
      ByteString(5, 6)
    )
  }

  it should "work if there are multiple elements to skip" in new Env {
    val input = Source(
      Vector(
        ByteString(1),
        ByteString(2, 3, 4),
        ByteString(5, 6)
      )
    )
    collectSource(input.via(ByteSkipper.make(5))) shouldBe Seq(
      ByteString(6)
    )
  }

}

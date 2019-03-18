package ai.mantik.ds.helper.akka

import ai.mantik.ds.testutil.{ GlobalAkkaSupport, TestBase }
import akka.stream.scaladsl.Source
import akka.util.ByteString

class SameSizeFramerSpec extends TestBase with GlobalAkkaSupport {

  trait Env {
    val sameSize = SameSizeFramer.make(2)
  }

  it should "work for empty" in new Env {
    val input = Source.apply[ByteString](
      Vector.empty
    )
    collectSource(input.via(sameSize)) shouldBe empty
  }

  it should "work for a typical example" in new Env {
    val input = Source.apply[ByteString](
      Vector(
        ByteString(),
        ByteString(1),
        ByteString(2, 3),
        ByteString(4),
        ByteString(5, 6, 7, 8, 9),
        ByteString(10)
      )
    )
    collectSource(input.via(sameSize)) shouldBe Seq(
      ByteString(1, 2),
      ByteString(3, 4),
      ByteString(5, 6),
      ByteString(7, 8),
      ByteString(9, 10)
    )
  }

  it should "cap missing data" in new Env {
    val input = Source.apply(
      Vector(
        ByteString(1, 2),
        ByteString(3),
        ByteString(4),
        ByteString(5)
      )
    )
    collectSource(input.via(sameSize)) shouldBe Seq(
      ByteString(1, 2),
      ByteString(3, 4)
    )
  }

  it should "work if there is a lot of stuff pendign at the end" in new Env {
    val input = Source.apply(
      Vector(
        ByteString(0),
        ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9)
      )
    )
    collectSource(input.via(sameSize)) shouldBe Seq(
      ByteString(0, 1),
      ByteString(2, 3),
      ByteString(4, 5),
      ByteString(6, 7),
      ByteString(8, 9)
    )
  }
}

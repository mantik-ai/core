package ai.mantik.executor.model

import ai.mantik.testutils.TestBase
import akka.util.ByteString

class ByteStringCodecSpec extends TestBase {

  def test(b: ByteString): Unit = {
    val encoded = ByteStringCodec.encoder(b)
    val decoded = ByteStringCodec.decoder.decodeJson(encoded)
    decoded shouldBe Right(b)
  }

  it should "work" in {
    test(ByteString.empty)
    test(ByteString.fromString("Hello World"))
  }
}

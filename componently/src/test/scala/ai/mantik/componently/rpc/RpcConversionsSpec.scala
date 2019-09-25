package ai.mantik.componently.rpc

import java.time.Instant

import ai.mantik.testutils.TestBase

class RpcConversionsSpec extends TestBase {

  "instant encoding" should "work" in {
    val i = Instant.parse("2019-09-19T12:14:15.123456789Z")
    val encoded = RpcConversions.encodeInstant(i)
    encoded.getSeconds shouldBe i.getEpochSecond
    encoded.getNanos shouldBe i.getNano
    val back = RpcConversions.decodeInstant(encoded)
    back shouldBe i
  }
}

package ai.mantik.planner.repository.rpc

import ai.mantik.testutils.{ AkkaSupport, TestBase }
import akka.stream.scaladsl.{ Keep, Sink }

class StreamConversionsSpec extends TestBase with AkkaSupport {

  trait StreamObserverEnv {
    val source = StreamConversions.streamObserverSource[Int]()
    val sink = Sink.seq[Int]
    val (observer, valuesFuture) = source.toMat(sink)(Keep.both).run()
  }

  "streamObserverSource" should "generate a source, generating a stream observer" in new StreamObserverEnv {
    observer.onNext(10)
    observer.onNext(11)
    observer.onCompleted()
    val values = await(valuesFuture)
    values shouldBe Seq(10, 11)
  }

  it should "work for an empty case" in new StreamObserverEnv {
    observer.onCompleted()
    val values = await(valuesFuture)
    values shouldBe empty
  }

  it should "handle errors" in new StreamObserverEnv {
    val rt = new RuntimeException("Boom")
    observer.onNext(10)
    observer.onError(rt)
    intercept[RuntimeException] {
      await(valuesFuture)
    } shouldBe rt
  }
}

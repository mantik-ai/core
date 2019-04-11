package ai.mantik.planner.impl

import ai.mantik.testutils.{ AkkaSupport, TestBase }

import scala.concurrent.{ Future, TimeoutException }
import scala.concurrent.duration._

class FutureHelperSpec extends TestBase with AkkaSupport {

  val dummyException = new RuntimeException("Boom")

  "time" should "time a method" in {
    await(FutureHelper.time(logger, "Sample") {
      Future.successful(5)
    }) shouldBe 5

    val result = FutureHelper.time(logger, "Failing Sample") {
      Future.failed(dummyException)
    }
    intercept[RuntimeException] {
      await(result)
    } shouldBe dummyException
  }

  "afterEachOtherStateful" should "call multiple methods afterwards" in {
    def testFun(state: Int, value: String): Future[Int] = {
      Future.successful {
        state + value.length
      }
    }
    await(FutureHelper.afterEachOtherStateful(Nil, 5)(testFun)) shouldBe 5
    await(FutureHelper.afterEachOtherStateful(List("AB", "CDE"), 5)(testFun)) shouldBe 10

    var call = 0
    def failOn2nd(state: Int, value: String): Future[Int] = {
      call += 1
      if (call == 2) {
        Future.failed(dummyException)
      } else {
        testFun(state, value)
      }
    }

    val result = FutureHelper.afterEachOtherStateful(List("A", "BC", "DEF"), 5)(failOn2nd)
    intercept[RuntimeException] {
      await(result)
    } shouldBe dummyException
    call shouldBe 2
  }

  "tryMultipleTimes" should "work" in {
    var calls = 0

    def succeedAfterThree(): Future[Option[Int]] = {
      calls += 1
      if (calls == 3) {
        Future.successful(Some(100))
      } else {
        Future.successful(None)
      }
    }
    val result = FutureHelper.tryMultipleTimes(1.second, 10.millis) {
      succeedAfterThree()
    }

    await(result) shouldBe 100
    calls shouldBe 3
  }

  it should "fail on exception" in {
    var calls = 0
    val result = FutureHelper.tryMultipleTimes(1.second, 10.millis) {
      calls += 1
      Future.failed(dummyException)
    }
    intercept[RuntimeException] {
      await(result)
    } shouldBe dummyException
    calls shouldBe 1
  }

  it should "fail after timeout" in {
    var calls = 0
    val result = FutureHelper.tryMultipleTimes(100.millisecond, 25.millis) {
      calls += 1
      Future.successful(None)
    }
    intercept[TimeoutException] {
      await(result)
    }
    calls shouldBe <=(5)
    calls shouldBe >=(2)
  }
}

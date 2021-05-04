/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.componently.utils

import ai.mantik.testutils.{AkkaSupport, TestBase}

import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}

class FutureHelperSpec extends TestBase with AkkaSupport {

  val dummyException = new RuntimeException("Boom")

  "time" should "time a method" in {
    await(FutureHelper.time(logger, "Sample") {
      Future.successful(5)
    }) shouldBe 5

    val result = FutureHelper.time(logger, "Failing Sample") {
      Future.failed(dummyException)
    }
    awaitException[RuntimeException] {
      result
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
    awaitException[RuntimeException] {
      result
    } shouldBe dummyException
    call shouldBe 2
  }

  "afterEachOther" should "call multiple methods" in {
    def testFun(x: Int): Future[Int] = {
      Future.successful(x + 1)
    }
    await(FutureHelper.afterEachOther(Seq(1, 2))(testFun)) shouldBe Seq(2, 3)
    await(FutureHelper.afterEachOther(Nil)(testFun)) shouldBe Nil

    var called = 0
    def failOn2(x: Int): Future[Int] = {
      called += 1
      if (x == 2) {
        Future.failed(dummyException)
      } else {
        Future.successful(x + 1)
      }
    }
    val result = FutureHelper.afterEachOther(Seq(0, 1, 2, 3, 4))(failOn2)
    awaitException[RuntimeException] {
      result
    } shouldBe dummyException
    called shouldBe 3
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
    awaitException[RuntimeException] {
      result
    } shouldBe dummyException
    calls shouldBe 1
  }

  it should "fail after timeout" in {
    var calls = 0
    val result = FutureHelper.tryMultipleTimes(100.millisecond, 25.millis) {
      calls += 1
      Future.successful(None)
    }
    awaitException[TimeoutException] {
      result
    }
    calls shouldBe <=(5)
    calls shouldBe >=(2)
  }
}

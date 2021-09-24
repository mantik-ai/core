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

import ai.mantik.componently.utils.Tracked.{EndOfLiveException, ShutdownException}
import ai.mantik.testutils.{AkkaSupport, TestBase}

import scala.concurrent.duration._

class TrackedSpec extends TestBase with AkkaSupport with GlobalLocalAkkaRuntime {

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    enterTestcase()
  }

  override protected def afterEach(): Unit = {
    exitTestcase()
    super.afterEach()
  }

  trait Env {
    implicit val trackingContext = new TrackingContext(100.millis)
    val test = new Tracked[String]("Hello")
  }

  it should "version objects" in new Env {
    test.get() shouldBe ("Hello", 1)
    test.update { x =>
      x + "2"
    } shouldBe ("Hello2", 2)
    test.get() shouldBe ("Hello2", 2)
  }

  it should "be possible track objects" in new Env {
    trackingContext.callbackCount shouldBe 0
    trackingContext.callbackByTrackedCount(test) shouldBe 0

    await(test.monitor(0)) shouldBe ("Hello", 1) // fast path
    val result = test.monitor(1)

    trackingContext.callbackCount shouldBe 1
    trackingContext.callbackByTrackedCount(test) shouldBe 1

    test.update(x => x + "2")
    await(result) shouldBe ("Hello2", 2)
    val result2 = test.monitor(2)
    test.update(x => x + "3")
    await(result2) shouldBe ("Hello23", 3)

    trackingContext.callbackCount shouldBe 0
    trackingContext.callbackByTrackedCount(test) shouldBe 0
  }

  it should "be possible to track the same object twice" in new Env {
    val t1 = test.monitor(1)
    val t2 = test.monitor(1)
    test.update(x => x + "2")
    await(t1) shouldBe ("Hello2", 2)
    await(t2) shouldBe ("Hello2", 2)

    trackingContext.callbackCount shouldBe 0
    trackingContext.callbackByTrackedCount(test) shouldBe 0
  }

  it should "be possible to mark end of objects" in new Env {
    val f = test.monitor(1)
    test.markEndOfLive()
    intercept[EndOfLiveException] {
      test.get()
    }
    awaitException[EndOfLiveException] {
      f
    }
  }

  it should "time out" in new Env {
    val result = test.monitor(1)
    val t0 = System.currentTimeMillis()
    await(result) shouldBe ("Hello", 1)
    val t1 = System.currentTimeMillis()
    (t1 - t0) shouldBe <=(200L)
  }

  it should "cleanup callbacks" in new Env {
    val cb = test.monitor(1)
    akkaRuntime.shutdown()
    awaitException[ShutdownException](cb).getMessage should include("Akka")
  }
}

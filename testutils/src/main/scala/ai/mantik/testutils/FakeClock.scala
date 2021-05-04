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
package ai.mantik.testutils

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId, ZoneOffset}

import scala.concurrent.duration.FiniteDuration

/** A User Modifiable Time clock. Useful for testing timeouts without having to wait. */
class FakeClock(zone: ZoneId = ZoneOffset.UTC, private var time: Instant = FakeClock.DefaultTime) extends Clock {
  override def getZone: ZoneId = zone

  override def withZone(zone: ZoneId): Clock = new FakeClock(zone, time)

  override def instant(): Instant = time

  /** Set a new current time. */
  def setTime(time: Instant): Unit = {
    this.time = time
  }

  /** Set the time to defaultTime + duration. */
  def setTimeOffset(duration: FiniteDuration): Unit = {
    setTime(FakeClock.DefaultTime.plus(duration.toNanos, ChronoUnit.NANOS))
  }

  /** Reset time to the default value. */
  def resetTime(): Unit = {
    this.time = FakeClock.DefaultTime
  }
}

object FakeClock {

  /** A complete arbitrary default time. */
  val DefaultTime = Instant.parse("2019-06-05T14:00:00Z")
}

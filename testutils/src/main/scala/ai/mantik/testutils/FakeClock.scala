package ai.mantik.testutils

import java.time.temporal.ChronoUnit
import java.time.{ Clock, Instant, ZoneId, ZoneOffset }

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
    setTime(
      FakeClock.DefaultTime.plus(duration.toNanos, ChronoUnit.NANOS))
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

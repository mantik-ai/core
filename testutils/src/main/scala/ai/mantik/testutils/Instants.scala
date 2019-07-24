package ai.mantik.testutils

import java.time.Instant

/** A neverending source of instants for testing purposes. */
object Instants {

  val Root = Instant.parse("2018-09-28T15:00:00Z")

  def makeInstant(hour: Int = 0, minute: Int = 0): Instant = Root.plusSeconds(hour * 3600).plusSeconds(minute * 60)
}

package ai.mantik.core

import ai.mantik.repository.MantikId

/** A Mantik Item. */
trait MantikItem {
  val source: Source

  /** Save an item back to Mantik. */
  def save(location: MantikId): Action.SaveAction = Action.SaveAction(this, location)
}
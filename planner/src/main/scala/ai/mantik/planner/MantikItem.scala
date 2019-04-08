package ai.mantik.planner

import ai.mantik.repository.{ MantikDefinition, MantikId, Mantikfile }

/**
 * A single Item inside the Planner DSL.
 * Can represent data or algorithms.
 */
trait MantikItem {
  type DefinitionType <: MantikDefinition

  val source: Source
  val mantikfile: Mantikfile[DefinitionType]

  /** Save an item back to Mantik. */
  def save(location: MantikId): Action.SaveAction = Action.SaveAction(this, location)
}

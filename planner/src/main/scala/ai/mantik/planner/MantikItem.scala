package ai.mantik.planner

import ai.mantik.ds.element.{ Bundle, SingleElementBundle, ValueEncoder }
import ai.mantik.repository.meta.MetaVariableException
import ai.mantik.repository.{ MantikDefinition, MantikId, Mantikfile }

/**
 * A single Item inside the Planner DSL.
 * Can represent data or algorithms.
 */
trait MantikItem {
  type DefinitionType <: MantikDefinition
  type OwnType <: MantikItem

  val source: Source
  val mantikfile: Mantikfile[DefinitionType]

  /** Save an item back to Mantik. */
  def save(location: MantikId): Action.SaveAction = Action.SaveAction(this, location)

  /**
   * Update Meta Variables.
   * @throws MetaVariableException (see [[ai.mantik.repository.meta.MetaJson.withMetaValues]])
   */
  def withMetaValues(values: (String, SingleElementBundle)*): OwnType = {
    val updatedMantikfile = mantikfile.withMetaValues(values: _*)
    withMantikfile(updatedMantikfile)
  }

  /**
   * Convenience function to udpate a single meta value.
   * Types are matched automatically if possible
   * @throws MetaVariableException (see [[ai.mantik.repository.meta.MetaJson.withMetaValues]]
   */
  def withMetaValue[T: ValueEncoder](name: String, value: T): OwnType = {
    withMetaValues(name -> Bundle.fundamental(value))
  }

  /** Override the mantik file (not this can be dangerous). */
  protected def withMantikfile(mantikfile: Mantikfile[DefinitionType]): OwnType
}

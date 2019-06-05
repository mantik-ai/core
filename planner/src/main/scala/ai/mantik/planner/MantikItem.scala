package ai.mantik.planner

import ai.mantik.ds.element.{ Bundle, SingleElementBundle, ValueEncoder }
import ai.mantik.repository.meta.MetaVariableException
import ai.mantik.repository.{ AlgorithmDefinition, ContentTypes, DataSetDefinition, MantikArtifact, MantikDefinition, MantikId, Mantikfile, TrainableAlgorithmDefinition }

import scala.reflect.ClassTag

/**
 * A single Item inside the Planner DSL.
 * Can represent data or algorithms.
 */
trait MantikItem {
  type DefinitionType <: MantikDefinition
  type OwnType <: MantikItem

  val source: Source
  private[planner] val mantikfile: Mantikfile[DefinitionType]

  /** Save an item back to Mantik. */
  def save(location: MantikId): Action.SaveAction = Action.SaveAction(this, location)

  /** Returns the type's stack. */
  def stack: String = mantikfile.definition.stack

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

object MantikItem {

  /** Convert a (loaded) [[MantikArtifact]] to a [[MantikItem]]. */
  private[mantik] def fromMantikArtifact(artifact: MantikArtifact): MantikItem = {
    val source = artifact.fileId.map { fileId =>
      Source.Loaded(fileId, ContentTypes.ZipFileContentType)
    }.getOrElse(Source.Empty)

    def forceExtract[T <: MantikDefinition: ClassTag]: Mantikfile[T] = artifact.mantikfile.cast[T].right.get

    artifact.mantikfile.definition match {
      case a: AlgorithmDefinition          => Algorithm(source, forceExtract)
      case d: DataSetDefinition            => DataSet(source, forceExtract)
      case t: TrainableAlgorithmDefinition => TrainableAlgorithm(source, forceExtract)
    }
  }

}
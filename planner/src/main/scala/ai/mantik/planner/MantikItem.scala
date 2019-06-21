package ai.mantik.planner

import ai.mantik.ds.element.{ Bundle, SingleElementBundle, ValueEncoder }
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ AlgorithmDefinition, DataSetDefinition, MantikDefinition, MantikId, Mantikfile, PipelineDefinition, TrainableAlgorithmDefinition }
import ai.mantik.planner.pipelines.{ PipelineBuilder, PipelineResolver }
import ai.mantik.elements.meta.MetaVariableException
import ai.mantik.planner.repository.{ ContentTypes, MantikArtifact }

import scala.reflect.ClassTag

/**
 * A single Item inside the Planner DSL.
 * Can represent data or algorithms.
 */
trait MantikItem {
  type DefinitionType <: MantikDefinition
  type OwnType <: MantikItem

  /** Returns where the Item comes from. */
  private[mantik] def source: Source
  /** Returns where the item payload comes from. */
  private[mantik] def payloadSource: PayloadSource = source.payload
  /** Returns the item's Mantikfile with definition. */
  private[planner] val mantikfile: Mantikfile[DefinitionType]

  /** Save an item back to Mantik. */
  def save(location: MantikId): Action.SaveAction = Action.SaveAction(this, location)

  /** Returns the type's stack. */
  def stack: String = mantikfile.definition.stack

  /**
   * Returns the [[MantikId]] of the item if it was directly loaded.
   * Returns None if the item was modified or derived.
   */
  def mantikId: Option[MantikId] = source.definition match {
    case DefinitionSource.Loaded(id) => Some(id)
    case _                           => None
  }

  /**
   * Update Meta Variables.
   *
   * @throws MetaVariableException (see [[ai.mantik.elements.meta.MetaJson.withMetaValues]])
   */
  def withMetaValues(values: (String, SingleElementBundle)*): OwnType = {
    val updatedMantikfile = mantikfile.withMetaValues(values: _*)
    withMantikfile(updatedMantikfile)
  }

  /**
   * Convenience function to udpate a single meta value.
   * Types are matched automatically if possible
   *
   * @throws MetaVariableException (see [[ai.mantik.elements.meta.MetaJson.withMetaValues]]
   */
  def withMetaValue[T: ValueEncoder](name: String, value: T): OwnType = {
    withMetaValues(name -> Bundle.fundamental(value))
  }

  /** Override the mantik file (not this can be dangerous). */
  protected def withMantikfile(mantikfile: Mantikfile[DefinitionType]): OwnType
}

/** A Mantik Item which can be applied to DataSets (e.g. Algorithms). */
trait ApplicableMantikItem extends MantikItem {

  /** The function type of this item. */
  def functionType: FunctionType

  def apply(data: DataSet): DataSet = {
    val adapted = data.autoAdaptOrFail(functionType.input)

    DataSet.natural(
      Source.constructed(
        PayloadSource.OperationResult(Operation.Application(this, adapted))
      ),
      functionType.output
    )
  }
}

object MantikItem {

  /** Convert a (loaded) [[MantikArtifact]] to a [[MantikItem]]. */
  private[mantik] def fromMantikArtifact(artifact: MantikArtifact, hull: Seq[MantikArtifact] = Seq.empty): MantikItem = {
    val payloadSource = artifact.fileId.map { fileId =>
      PayloadSource.Loaded(fileId, ContentTypes.ZipFileContentType)
    }.getOrElse(PayloadSource.Empty)

    val source = Source(
      DefinitionSource.Loaded(artifact.id),
      payloadSource
    )

    def forceExtract[T <: MantikDefinition: ClassTag]: Mantikfile[T] = artifact.mantikfile.cast[T].right.get

    artifact.mantikfile.definition match {
      case a: AlgorithmDefinition          => Algorithm(source, forceExtract)
      case d: DataSetDefinition            => DataSet(source, forceExtract)
      case t: TrainableAlgorithmDefinition => TrainableAlgorithm(source, forceExtract)
      case p: PipelineDefinition =>
        val referenced = hull.map { item =>
          item.id -> fromMantikArtifact(item)
        }.toMap
        PipelineBuilder.buildOrFailFromMantikfile(source.definition, forceExtract, referenced)
    }
  }

}
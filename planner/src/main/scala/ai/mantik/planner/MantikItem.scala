package ai.mantik.planner

import ai.mantik.ds.element.{ Bundle, SingleElementBundle, ValueEncoder }
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ AlgorithmDefinition, DataSetDefinition, ItemId, MantikDefinition, MantikId, Mantikfile, PipelineDefinition, TrainableAlgorithmDefinition }
import ai.mantik.planner.pipelines.{ PipelineBuilder, PipelineResolver }
import ai.mantik.elements.meta.MetaVariableException
import ai.mantik.planner.repository.{ ContentTypes, Errors, MantikArtifact }
import ai.mantik.planner.utils.AtomicReference

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
  private[planner] def mantikfile: Mantikfile[DefinitionType]

  /** The state of the mantik Item. */
  private[planner] val state: AtomicReference[MantikItemState] = new AtomicReference({
    val (mantikId, loaded): (Option[MantikId], Boolean) = source.definition match {
      case DefinitionSource.Loaded(mantikId, _) => Some(mantikId) -> true
      case _                                    => None -> false
    }
    val file = source.payload match {
      case PayloadSource.Loaded(fileId, _) => Some(fileId)
      case _                               => None
    }
    MantikItemState(mantikId, loaded, file)
  })

  /** Save an item back to Mantik. */
  def save(location: MantikId): Action.SaveAction = Action.SaveAction(this, location)

  /** Returns the type's stack. */
  def stack: String = mantikfile.definition.stack

  /**
   * Returns the [[MantikId]] of the Item. This can be anonymous.
   * Note: this doesn't mean that the item is stored.
   */
  def mantikId: MantikId = {
    state.get.mantikId.getOrElse(itemId.asAnonymousMantikId)
  }

  /**
   * Returns the [[ItemId]] of the item. This is a new value if the item was not loaded.
   */
  lazy val itemId: ItemId = source.definition match {
    case l: DefinitionSource.Loaded => l.itemId
    case _                          => ItemId.generate()
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

  /** Deploy the item. */
  def deploy(ingressName: Option[String] = None, nameHint: Option[String] = None): Action.Deploy = Action.Deploy(
    this, nameHint = nameHint, ingressName = ingressName
  )
}

object MantikItem {

  /** Convert a (loaded) [[MantikArtifact]] to a [[MantikItem]]. */
  private[mantik] def fromMantikArtifact(artifact: MantikArtifact, hull: Seq[MantikArtifact] = Seq.empty): MantikItem = {
    val payloadSource = artifact.fileId.map { fileId =>
      PayloadSource.Loaded(fileId, ContentTypes.ZipFileContentType)
    }.getOrElse(PayloadSource.Empty)

    val source = Source(
      DefinitionSource.Loaded(artifact.id, artifact.itemId),
      payloadSource
    )

    def forceExtract[T <: MantikDefinition: ClassTag]: Mantikfile[T] = artifact.mantikfile.cast[T].right.get

    val item = artifact.mantikfile.definition match {
      case a: AlgorithmDefinition          => Algorithm(source, forceExtract)
      case d: DataSetDefinition            => DataSet(source, forceExtract)
      case t: TrainableAlgorithmDefinition => TrainableAlgorithm(source, forceExtract)
      case p: PipelineDefinition =>
        val referenced = hull.map { item =>
          item.id -> fromMantikArtifact(item)
        }.toMap
        PipelineBuilder.buildOrFailFromMantikfile(source.definition, forceExtract, referenced)
    }

    artifact.deploymentInfo.foreach { deploymentInfo =>
      item.state.update {
        _.copy(
          deployment = Some(
            DeploymentState(
              name = deploymentInfo.name,
              internalUrl = deploymentInfo.internalUrl,
              externalUrl = deploymentInfo.externalUrl
            )
          )
        )
      }
    }

    item
  }

}
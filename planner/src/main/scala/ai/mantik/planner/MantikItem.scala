package ai.mantik.planner

import ai.mantik.ds.element.{ Bundle, SingleElementBundle, ValueEncoder }
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.{ AlgorithmDefinition, BridgeDefinition, DataSetDefinition, ItemId, MantikDefinition, MantikDefinitionWithBridge, MantikDefinitionWithoutBridge, MantikId, MantikHeader, NamedMantikId, PipelineDefinition, TrainableAlgorithmDefinition }
import ai.mantik.planner.pipelines.{ PipelineBuilder, PipelineResolver }
import ai.mantik.elements.meta.MetaVariableException
import ai.mantik.planner.repository.{ Bridge, ContentTypes, MantikArtifact }
import ai.mantik.planner.utils.AtomicReference

import scala.reflect.ClassTag

/**
 * A single Item inside the Planner DSL.
 * Can represent data or algorithms.
 */
trait MantikItem {
  type DefinitionType <: MantikDefinition
  type OwnType <: MantikItem

  private[mantik] val core: MantikItemCore[DefinitionType]

  /** Returns where the Item comes from. */
  private[mantik] def source: Source = core.source
  /** Returns where the item payload comes from. */
  private[mantik] def payloadSource: PayloadSource = source.payload
  /** Returns where the mantikHeader / item comes from. */
  private[mantik] def definitionSource: DefinitionSource = source.definition
  /** Returns the item's MantikHeader with definition. */
  private[mantik] def mantikHeader: MantikHeader[DefinitionType] = core.mantikHeader

  /** The state of the mantik Item. */
  private[planner] val state: AtomicReference[MantikItemState] = new AtomicReference({
    val file = source.payload match {
      case PayloadSource.Loaded(fileId, _) => Some(fileId)
      case _                               => None
    }
    MantikItemState(
      namedMantikItem = source.definition.name,
      itemStored = source.definition.itemStored,
      nameStored = source.definition.nameStored,
      payloadFile = file
    )
  })

  /**
   * Tag the item, giving it an additional name.
   *
   * Note: this will only have an effect, if the Item is saved or pushed.
   *
   * @return the tagged item.
   */
  def tag(name: NamedMantikId): OwnType = withCore(
    core.copy(
      source = source.copy(
        definition = DefinitionSource.Tagged(name, source.definition)
      )
    )
  )

  /** Save an item back in the local database */
  def save(): Action.SaveAction = Action.SaveAction(this)

  /** Pushes an item to the registry. */
  def push(): Action.PushAction = Action.PushAction(this)

  /**
   * Returns the [[NamedMantikId]] of the Item. This can be anonymous.
   * Note: this doesn't mean that the item is stored.
   */
  def mantikId: MantikId = {
    name.getOrElse(itemId)
  }

  /** Returns the name of the item, if it has one. */
  def name: Option[NamedMantikId] = {
    state.get.namedMantikItem
  }

  /**
   * Returns the [[ItemId]] of the item. This is a new value if the item was not loaded.
   */
  lazy val itemId: ItemId = source.definition.storedItemId.getOrElse(ItemId.generate())

  /**
   * Update Meta Variables.
   *
   * @throws MetaVariableException (see [[ai.mantik.elements.meta.MetaJson.withMetaValues]])
   */
  def withMetaValues(values: (String, SingleElementBundle)*): OwnType = {
    val updatedMantikHeader = mantikHeader.withMetaValues(values: _*)
    withMantikHeader(updatedMantikHeader)
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

  /** Override the mantik header (not this can be dangerous). */
  protected def withMantikHeader(mantikHeader: MantikHeader[DefinitionType]): OwnType = {
    withCore(
      MantikItemCore(
        source = source.derive,
        mantikHeader = mantikHeader,
        bridge = core.bridge
      )
    )
  }

  override def toString: String = {
    val builder = StringBuilder.newBuilder
    builder ++= getClass.getSimpleName
    builder += ' '
    builder ++= mantikId.toString
    val s = state.get
    if (s.itemStored) {
      builder ++= " itemStored"
    }
    if (s.nameStored) {
      builder ++= " nameStored"
    }
    builder.result()
  }

  /** Override the current source type. */
  protected def withCore(updated: MantikItemCore[DefinitionType]): OwnType
}

/** Contains the common base data for every MantikItem. */
case class MantikItemCore[T <: MantikDefinition](
    source: Source,
    mantikHeader: MantikHeader[T],
    bridge: Option[Bridge]
)

object MantikItemCore {
  def apply[T <: MantikDefinitionWithBridge](source: Source, mantikHeader: MantikHeader[T], bridge: Bridge): MantikItemCore[T] = {
    MantikItemCore(source, mantikHeader, Some(bridge))
  }

  def apply[T <: MantikDefinitionWithoutBridge](source: Source, mantikHeader: MantikHeader[T]): MantikItemCore[T] = {
    MantikItemCore(source, mantikHeader, None)
  }
}

trait BridgedMantikItem extends MantikItem {
  override type DefinitionType <: MantikDefinitionWithBridge

  /** Returns the type's bridge. */
  def bridgeMantikId: MantikId = mantikHeader.definition.bridge

  /** Returns the item bridge. */
  def bridge: Bridge = core.bridge.getOrElse {
    ErrorCodes.InternalError.throwIt("No bridge associated to this element")
  }
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

  /**
   * Convert a (loaded) [[MantikArtifact]] to a [[MantikItem]].
   * @param defaultItemLookup if true, default items are favorized.
   */
  private[mantik] def fromMantikArtifact(
    artifact: MantikArtifact, hull: Seq[MantikArtifact] = Seq.empty, defaultItemLookup: Boolean = true
  ): MantikItem = {
    val payloadSource = artifact.fileId.map { fileId =>
      PayloadSource.Loaded(fileId, ContentTypes.ZipFileContentType)
    }.getOrElse(PayloadSource.Empty)

    val source = Source(
      DefinitionSource.Loaded(artifact.namedId, artifact.itemId),
      payloadSource
    )

    def forceBridge(name: MantikId, forKind: String): Bridge = {
      Bridge.fromMantikArtifacts(name, hull, forKind)
    }

    val bridge = artifact.parsedMantikHeader.definition match {
      case d: MantikDefinitionWithBridge =>
        Some(forceBridge(d.bridge, artifact.parsedMantikHeader.definition.kind))
      case _ => None
    }

    def forceExtract[T <: MantikDefinition: ClassTag]: MantikItemCore[T] = {
      val mantikHeader = artifact.parsedMantikHeader.cast[T].right.get
      MantikItemCore(source, mantikHeader, bridge)
    }

    val item = artifact.parsedMantikHeader.definition match {
      case _: AlgorithmDefinition => Algorithm(forceExtract)
      case _: DataSetDefinition   => DataSet(forceExtract)
      case _: TrainableAlgorithmDefinition =>
        val extracted = forceExtract[TrainableAlgorithmDefinition]
        val trainedBridge = extracted.mantikHeader.definition.trainedBridge.map(forceBridge(_, MantikDefinition.AlgorithmKind))
          .orElse(bridge).getOrElse {
            ErrorCodes.MantikItemInvalidBridge.throwIt("Missing bridge for trainable algorithm definition")
          }
        TrainableAlgorithm(forceExtract, trainedBridge)
      case _: PipelineDefinition =>
        val referenced = hull.map { item =>
          val subHull = hull.filter(_.itemId != item.itemId)
          item.mantikId -> fromMantikArtifact(item, subHull)
        }.toMap
        PipelineBuilder.buildOrFailFromMantikHeader(source.definition, forceExtract[PipelineDefinition].mantikHeader, referenced)
      case _: BridgeDefinition =>
        Bridge(forceExtract)
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
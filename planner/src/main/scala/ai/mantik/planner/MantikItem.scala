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
package ai.mantik.planner

import ai.mantik.ds.element.{Bundle, SingleElementBundle, ValueEncoder}
import ai.mantik.ds.functional.FunctionType
import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.{
  AlgorithmDefinition,
  BridgeDefinition,
  DataSetDefinition,
  ItemId,
  MantikDefinition,
  MantikDefinitionWithBridge,
  MantikDefinitionWithoutBridge,
  MantikHeader,
  MantikId,
  NamedMantikId,
  PipelineDefinition,
  TrainableAlgorithmDefinition
}
import ai.mantik.planner.pipelines.{PipelineBuilder, PipelineResolver}
import ai.mantik.elements.meta.MetaVariableException
import ai.mantik.planner.impl.{MantikItemCodec, MantikItemStateManager}
import ai.mantik.planner.repository.{Bridge, ContentTypes, MantikArtifact}
import ai.mantik.planner.utils.AtomicReference
import io.circe.{Decoder, Encoder}

import scala.reflect.ClassTag

/**
  * A single Item inside the Planner API.
  * Can represent data or algorithms.
  * Can be serialized to JSON.
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

  /**
    * Explicitly set the item Id.
    * Note: this may be dangerous (itemIds are referenced from MantikState etc.)
    */
  private[mantik] def withItemId(itemId: ItemId): OwnType = withCore(
    core.copy(itemId = itemId)
  )

  /** Save an item back in the local database */
  def save(): Action.SaveAction = Action.SaveAction(this)

  /** Pushes an item to the registry. */
  def push(): Action.PushAction = Action.PushAction(this)

  /**
    * Return true if the item is requested for caching
    * (This doesn't have to mean that the cache is evaluated)
    */
  def isCached: Boolean = {
    def check(payloadSource: PayloadSource): Boolean = {
      payloadSource match {
        case _: PayloadSource.Cached     => true
        case p: PayloadSource.Projection => check(p.source)
        case _                           => false
      }
    }
    check(core.source.payload)
  }

  /** Returns the state of the item. */
  def state(implicit planningContext: PlanningContext): MantikItemState = {
    planningContext.state(this)
  }

  /**
    * Returns the [[ai.mantik.elements.ItemId]] of the item.
    */
  def itemId: ItemId = core.itemId

  /**
    * Returns the mantik id.
    * (Note: if it was stored after generating, it may not reflect the name)
    */
  def mantikId: MantikId = source.definition.name.getOrElse(itemId)

  /**
    * Update Meta Variables.
    */
  @throws[MetaVariableException]("If a value is missing or of wrong type or not changeable.")
  def withMetaValues(values: (String, SingleElementBundle)*): OwnType = {
    val updatedMantikHeader = mantikHeader.withMetaValues(values: _*)
    withMantikHeader(updatedMantikHeader)
  }

  /**
    * Convenience function to udpate a single meta value.
    * Types are matched automatically if possible
    */
  @throws[MetaVariableException]("If a value is missing or of wrong type or not changeable.")
  def withMetaValue[T: ValueEncoder](name: String, value: T): OwnType = {
    withMetaValues(name -> Bundle.fundamental(value))
  }

  /** Override the mantik header (not this can be dangerous). */
  protected def withMantikHeader(mantikHeader: MantikHeader[DefinitionType]): OwnType = {
    withCore(
      MantikItemCore(
        source = source.derive,
        mantikHeader = mantikHeader,
        bridge = core.bridge,
        itemId = ItemId.generate()
      )
    )
  }

  override def toString: String = {
    val builder = new StringBuilder()
    builder ++= getClass.getSimpleName
    builder += ' '
    builder ++= itemId.toString
    builder.result()
  }

  /** Override the current source type. */
  protected def withCore(updated: MantikItemCore[DefinitionType]): OwnType
}

/** Contains the common base data for every MantikItem. */
case class MantikItemCore[T <: MantikDefinition](
    source: Source,
    mantikHeader: MantikHeader[T],
    bridge: Option[Bridge],
    itemId: ItemId
)

object MantikItemCore {
  def apply[T <: MantikDefinitionWithBridge](
      source: Source,
      mantikHeader: MantikHeader[T],
      bridge: Bridge
  ): MantikItemCore[T] = {
    MantikItemCore(source, mantikHeader, Some(bridge), generateItemId(source))
  }

  def apply[T <: MantikDefinitionWithoutBridge](source: Source, mantikHeader: MantikHeader[T]): MantikItemCore[T] = {
    MantikItemCore(source, mantikHeader, None, generateItemId(source))
  }

  private[mantik] def generateItemId(source: Source): ItemId = {
    source.definition.storedItemId.getOrElse(ItemId.generate())
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
    this,
    nameHint = nameHint,
    ingressName = ingressName
  )
}

object MantikItem {

  // JSON Codec
  implicit val encoder: Encoder[MantikItem] = MantikItemCodec
  implicit val decoder: Decoder[MantikItem] = MantikItemCodec

  /**
    * Convert a (loaded) [[MantikArtifact]] to a [[MantikItem]].
    * @param defaultItemLookup if true, default items are favorized.
    */
  private[mantik] def fromMantikArtifact(
      artifact: MantikArtifact,
      mantikItemStateManager: MantikItemStateManager,
      hull: Seq[MantikArtifact] = Seq.empty,
      defaultItemLookup: Boolean = true
  ): MantikItem = {
    val payloadSource = artifact.fileId
      .map { fileId =>
        PayloadSource.Loaded(fileId, ContentTypes.ZipFileContentType)
      }
      .getOrElse(PayloadSource.Empty)

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
      val mantikHeader = artifact.parsedMantikHeader.cast[T].fold(e => throw e, identity)
      MantikItemCore(source, mantikHeader, bridge, MantikItemCore.generateItemId(source))
    }

    val item = artifact.parsedMantikHeader.definition match {
      case _: AlgorithmDefinition => Algorithm(forceExtract)
      case _: DataSetDefinition   => DataSet(forceExtract)
      case _: TrainableAlgorithmDefinition =>
        val extracted = forceExtract[TrainableAlgorithmDefinition]
        val trainedBridge = extracted.mantikHeader.definition.trainedBridge
          .map(forceBridge(_, MantikDefinition.AlgorithmKind))
          .orElse(bridge)
          .getOrElse {
            ErrorCodes.MantikItemInvalidBridge.throwIt("Missing bridge for trainable algorithm definition")
          }
        TrainableAlgorithm(forceExtract, trainedBridge)
      case _: PipelineDefinition =>
        val referenced = hull.map { item =>
          val subHull = hull.filter(_.itemId != item.itemId)
          item.mantikId -> fromMantikArtifact(item, mantikItemStateManager, subHull)
        }.toMap
        PipelineBuilder.buildOrFailFromMantikHeader(
          source.definition,
          forceExtract[PipelineDefinition].mantikHeader,
          referenced
        )
      case _: BridgeDefinition =>
        Bridge(forceExtract)
    }

    artifact.deploymentInfo.foreach { deploymentInfo =>
      mantikItemStateManager.upsert(
        item,
        _.copy(
          deployment = Some(
            DeploymentState(
              name = deploymentInfo.name,
              internalUrl = deploymentInfo.internalUrl,
              externalUrl = deploymentInfo.externalUrl
            )
          )
        )
      )
    }

    item
  }
}

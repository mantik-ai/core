package ai.mantik.planner.repository

import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.{BridgeDefinition, ItemId, MantikDefinition, MantikId, MantikHeader, NamedMantikId}
import ai.mantik.planner.{BuiltInItems, DefinitionSource, MantikItem, MantikItemCore, PayloadSource, Source}

/** A Bridge */
case class Bridge(core: MantikItemCore[BridgeDefinition]) extends MantikItem {
  override type DefinitionType = BridgeDefinition
  override type OwnType = Bridge

  override protected def withCore(updated: MantikItemCore[BridgeDefinition]): Bridge = {
    copy(core = updated)
  }
}

object Bridge {

  def apply(
      definitionSource: DefinitionSource,
      mantikHeader: MantikHeader[BridgeDefinition]
  ): Bridge = {
    Bridge(
      MantikItemCore(
        source = Source(
          definitionSource,
          payload = PayloadSource.Empty
        ),
        mantikHeader
      )
    )
  }

  val naturalBridge = BuiltInItems.NaturalBridge
  val selectBridge = BuiltInItems.SelectBridge

  /**
    * Load a Bridge from a sequence of MantikArtifacts
    * Built in Bridges are favorized.
    */
  def fromMantikArtifacts(
      name: MantikId,
      artifacts: Seq[MantikArtifact],
      forKind: String
  ): Bridge = {
    BuiltInItems.readBuiltInItem(name) match {
      case Some(b: Bridge) => return b
      case Some(other) =>
        ErrorCodes.MantikItemWrongType.throwIt(s"Expected bridge got ${other.mantikHeader.definition.kind}")
      case None => // continue
    }
    val bridgeArtifact = artifacts.find(_.namedId.contains(name)).getOrElse {
      ErrorCodes.MantikItemNotFound.throwIt(s"Missing bridge ${name}")
    }
    val mantikHeader = bridgeArtifact.parsedMantikHeader.cast[BridgeDefinition].right.getOrElse {
      ErrorCodes.MantikItemWrongType
        .throwIt(s"${name} references a a${bridgeArtifact.parsedMantikHeader.definition.kind}, expected bridge")
    }
    val bridge = Bridge(
      DefinitionSource.Loaded(bridgeArtifact.namedId, bridgeArtifact.itemId),
      mantikHeader
    )
    if (!bridge.core.mantikHeader.definition.suitable.contains(forKind)) {
      ErrorCodes.MantikItemInvalidBridge.throwIt(s"Bridge ${name} not suitable for ${forKind}")
    }
    bridge
  }
}

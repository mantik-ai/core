package ai.mantik.planner.repository

import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.{ BridgeDefinition, ItemId, MantikDefinition, MantikId, Mantikfile, NamedMantikId }
import ai.mantik.planner.{ BuiltInItems, DefinitionSource, MantikItem, MantikItemCore, PayloadSource, Source }

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
    mantikfile: Mantikfile[BridgeDefinition]
  ): Bridge = {
    Bridge(
      MantikItemCore(
        source = Source(
          definitionSource,
          payload = PayloadSource.Empty
        ),
        mantikfile
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
      case Some(other)     => ErrorCodes.MantikItemWrongType.throwIt(s"Expected bridge got ${other.mantikfile.definition.kind}")
      case None            => // continue
    }
    val bridgeArtifact = artifacts.find(_.namedId.contains(name)).getOrElse {
      ErrorCodes.MantikItemNotFound.throwIt(s"Missing bridge ${name}")
    }
    val mantikfile = bridgeArtifact.parsedMantikfile.cast[BridgeDefinition].right.getOrElse {
      ErrorCodes.MantikItemWrongType.throwIt(s"${name} references a a${bridgeArtifact.parsedMantikfile.definition.kind}, expected bridge")
    }
    val bridge = Bridge(
      DefinitionSource.Loaded(bridgeArtifact.namedId, bridgeArtifact.itemId),
      mantikfile
    )
    if (!bridge.core.mantikfile.definition.suitable.contains(forKind)) {
      ErrorCodes.MantikItemInvalidBridge.throwIt(s"Bridge ${name} not suitable for ${forKind}")
    }
    bridge
  }
}

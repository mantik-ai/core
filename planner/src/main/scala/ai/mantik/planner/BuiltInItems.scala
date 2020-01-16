package ai.mantik.planner

import ai.mantik.elements.{ BridgeDefinition, ItemId, MantikDefinition, MantikId, MantikHeader, NamedMantikId }
import ai.mantik.planner.repository.{ Bridge, ContentTypes }

/** Built in Items in Mantik */
object BuiltInItems {

  /** The protected name for built in Items. */
  val BuiltInAccount = "builtin"

  /** Name of the Natural Format. */
  val NaturalBridgeName = NamedMantikId(account = BuiltInAccount, name = "natural")

  /** Name of the Select Bridge */
  val SelectBridgeName = NamedMantikId(account = BuiltInAccount, name = "select")

  /**
   * Look for a Built In Item
   * @return the item if it was found.
   */
  def readBuiltInItem(mantikId: MantikId): Option[MantikItem] = {
    mantikId match {
      case NaturalBridgeName => Some(NaturalBridge)
      case SelectBridgeName  => Some(SelectBridge)
      case _                 => None
    }
  }

  /** The Natural Bridge. */
  val NaturalBridge = Bridge(
    MantikItemCore(
      Source(
        DefinitionSource.Loaded(Some(NaturalBridgeName), ItemId("@1")),
        PayloadSource.Empty
      ),
      MantikHeader.pure(
        BridgeDefinition(
          dockerImage = "",
          suitable = Seq(MantikDefinition.DataSetKind),
          protocol = 0,
          payloadContentType = Some(ContentTypes.MantikBundleContentType)
        )
      )
    )
  )

  /** The Embedded Select Bridge. */
  val SelectBridge = Bridge(
    MantikItemCore(
      Source(
        DefinitionSource.Loaded(Some(SelectBridgeName), ItemId("@2")),
        PayloadSource.Empty
      ),
      MantikHeader.pure(
        BridgeDefinition(
          dockerImage = "mantikai/bridge.select",
          suitable = Seq(MantikDefinition.DataSetKind),
          protocol = 1,
          payloadContentType = None
        )
      )
    )
  )
}

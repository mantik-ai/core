package ai.mantik.planner

import ai.mantik.elements.NamedMantikId
import io.circe.{ Encoder, Decoder }
import io.circe.generic.semiauto

/**
 * The current run time state of a Mantik Item.
 *
 * @param namedMantikItem the mantik id if the Item is stored/loaded inside the repository.
 * @param itemStored the item itself is stored (this doesn't require that it has a mantik id)
 * @param nameStored the name is stored (this also requires that the item is stored).
 * @param deployment information about deployment
 * @param cacheFile temporary cache file holding the result
 */
case class MantikItemState(
    namedMantikItem: Option[NamedMantikId] = None,
    itemStored: Boolean = false,
    nameStored: Boolean = false,
    payloadFile: Option[String] = None,
    deployment: Option[DeploymentState] = None,
    cacheFile: Option[String] = None
)

object MantikItemState {
  /** Initialize a new MantikItemState from source. */
  def initializeFromSource(source: Source): MantikItemState = {
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
  }

  implicit val encoder: Encoder[MantikItemState] = semiauto.deriveEncoder[MantikItemState]
  implicit val decoder: Decoder[MantikItemState] = semiauto.deriveDecoder[MantikItemState]
}

/** Deployment information of one [[MantikItem]]. */
case class DeploymentState(
    name: String,
    internalUrl: String,
    externalUrl: Option[String] = None
)

object DeploymentState {
  implicit val encoder: Encoder[DeploymentState] = semiauto.deriveEncoder[DeploymentState]
  implicit val decoder: Decoder[DeploymentState] = semiauto.deriveDecoder[DeploymentState]
}

package ai.mantik.planner

import ai.mantik.elements.{ ItemId, NamedMantikId }

/**
 * The current run time state of a Mantik Item.
 * Note: this state is mutable inside a Mantik Item.
 *
 * @param namedMantikItem the mantik id if the Item is stored/loaded inside the repository.
 * @param itemStored the item itself is stored (this doesn't require that it has a mantik id)
 * @param nameStored the name is stored (this also requires that the item is stored).
 * @param deployment information about deployment
 */
case class MantikItemState(
    namedMantikItem: Option[NamedMantikId] = None,
    itemStored: Boolean = false,
    nameStored: Boolean = false,
    payloadFile: Option[String] = None,
    deployment: Option[DeploymentState] = None
)

/** Deployment information of one [[MantikItem]]. */
case class DeploymentState(
    name: String,
    internalUrl: String,
    externalUrl: Option[String] = None
)

package ai.mantik.planner

import ai.mantik.elements.{ ItemId, NamedMantikId }

/**
 * The current run time state of a Mantik Item.
 * Note: this state is mutable inside a Mantik Item.
 *
 * @param namedMantikItem the mantik id if the Item is stored/loaded inside the repository.
 * @param isStored the item is stored (this doesn't require that it has a mantik Id).
 * @param deployment information about deployment
 */
case class MantikItemState(
    namedMantikItem: Option[NamedMantikId] = None,
    isStored: Boolean = false,
    payloadFile: Option[String] = None,
    deployment: Option[DeploymentState] = None
)

/** Deployment information of one [[MantikItem]]. */
case class DeploymentState(
    name: String,
    internalUrl: String,
    externalUrl: Option[String] = None
)

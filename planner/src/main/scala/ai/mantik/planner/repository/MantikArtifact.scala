package ai.mantik.planner.repository

import java.time.Instant

import ai.mantik.elements.{ ItemId, MantikDefinition, MantikId, Mantikfile }

/** A Mantik Artefact. */
case class MantikArtifact(
    mantikfile: Mantikfile[_ <: MantikDefinition],
    fileId: Option[String],
    id: MantikId,
    itemId: ItemId,
    deploymentInfo: Option[DeploymentInfo] = None
)

/** Deployment Information as being stored in the [[Repository]]. */
case class DeploymentInfo(
    name: String,
    url: String,
    timestamp: Instant
)


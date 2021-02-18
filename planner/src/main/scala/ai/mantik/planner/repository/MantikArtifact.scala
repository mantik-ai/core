package ai.mantik.planner.repository

import java.time.Instant

import ai.mantik.elements.errors.InvalidMantikHeaderException
import ai.mantik.elements.{ ItemId, MantikDefinition, MantikId, MantikHeader, NamedMantikId }

/**
 * A Mantik Artefact.
 * @param mantikHeader the MantikHeader.
 * @param fileId associated file.
 * @param namedId an optional name, if it's not an anonymous item.
 * @param itemId the itemId
 * @param deploymentInfo optional current deployment info.
 * @param executorStorageId id of the payload mirrored in executor storage
 */
case class MantikArtifact(
    mantikHeader: String,
    fileId: Option[String],
    namedId: Option[NamedMantikId],
    itemId: ItemId,
    deploymentInfo: Option[DeploymentInfo] = None,
    executorStorageId: Option[String] = None
) {
  /** Returns the named Mantik Id if given, or the itemId as fallback */
  def mantikId: MantikId = namedId.getOrElse(itemId)

  /** The parsed MantikHeader. */
  lazy val parsedMantikHeader: MantikHeader[_ <: MantikDefinition] = MantikHeader.fromYaml(mantikHeader).fold(e => throw InvalidMantikHeaderException.wrap(e), identity)
}

object MantikArtifact {
  /** Build an Artifact from a MantikDefinifion */
  def makeFromDefinition[T <: MantikDefinition](
    mantikDefinition: MantikDefinition,
    name: NamedMantikId,
    fileId: Option[String] = None
  ): MantikArtifact = {
    MantikArtifact(
      mantikHeader = MantikHeader.pure(mantikDefinition).toJson,
      fileId = fileId,
      namedId = Some(name),
      itemId = ItemId.generate(),
      deploymentInfo = None
    )
  }
}

/**
 * Deployment Information as being stored in the [[Repository]].
 *
 * @param name of the node in the Executor
 * @param internalUrl MNP Url under which its reachable.
 * @param externalUrl optional external HTTP Url
 * @param timestamp timestamp
 * @param sub sub deployment infos for sub nodes.
 */
case class DeploymentInfo(
    name: String,
    internalUrl: String,
    externalUrl: Option[String] = None,
    timestamp: Instant,
    sub: Map[String, SubDeploymentInfo] = Map.empty
)

/** Sub Part deployment infos (e.g. for embedded SQL Nodes) */
case class SubDeploymentInfo(
    name: String,
    internalUrl: String
)


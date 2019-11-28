package ai.mantik.planner.repository

import java.time.Instant

import ai.mantik.elements.errors.InvalidMantikfileException
import ai.mantik.elements.{ ItemId, MantikDefinition, MantikId, Mantikfile, NamedMantikId }

/**
 * A Mantik Artefact.
 * @param mantikfile the Mantikfile.
 * @param fileId associated file.
 * @param namedId an optional name, if it's not an anonymous item.
 * @param itemId the itemId
 * @param deploymentInfo optional current deployment info.
 */
case class MantikArtifact(
    mantikfile: String,
    fileId: Option[String],
    namedId: Option[NamedMantikId],
    itemId: ItemId,
    deploymentInfo: Option[DeploymentInfo] = None
) {
  /** Returns the named Mantik Id if given, or the itemId as fallback */
  def mantikId: MantikId = namedId.getOrElse(itemId)

  /** The parsed Mantikfile. */
  lazy val parsedMantikfile: Mantikfile[_ <: MantikDefinition] = Mantikfile.fromYaml(mantikfile).fold(e => throw InvalidMantikfileException.wrap(e), identity)
}

object MantikArtifact {
  /** Build an Artifact from a MantikDefinifion */
  def makeFromDefinition[T <: MantikDefinition](
    mantikDefinition: MantikDefinition,
    name: NamedMantikId,
    fileId: Option[String] = None
  ): MantikArtifact = {
    MantikArtifact(
      mantikfile = Mantikfile.pure(mantikDefinition).toJson,
      fileId = fileId,
      namedId = Some(name),
      itemId = ItemId.generate(),
      deploymentInfo = None
    )
  }
}

/** Deployment Information as being stored in the [[Repository]]. */
case class DeploymentInfo(
    name: String,
    internalUrl: String,
    externalUrl: Option[String] = None,
    timestamp: Instant
)


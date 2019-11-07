package ai.mantik.planner.repository

import ai.mantik.componently.Component
import ai.mantik.elements.errors.{ ErrorCodes, MantikException }
import ai.mantik.elements.{ ItemId, MantikId, NamedMantikId }
import ai.mantik.planner.repository.MantikRegistry.PayloadSource
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Represents the MantikRegistry, which handles
 * MantikArtifacts payload files.
 *
 * This can either be local or remote (Mantik Hub).
 */
trait MantikRegistry extends Component {

  /** Retrieves an Artifact from the Mantik Registry. */
  def get(mantikId: MantikId): Future[MantikArtifact]

  /** Like get, but returns None if the item doesn't exist. */
  def maybeGet(mantikId: MantikId)(implicit ec: ExecutionContext): Future[Option[MantikArtifact]] = {
    get(mantikId).map(Some(_)).recover {
      case e: MantikException if e.code == ErrorCodes.MantikItemNotFound => None
    }
  }

  /** Retrieves item payload from Mantik Registry. */
  def getPayload(fileId: String): Future[PayloadSource]

  /**
   * Add a Mantik Artifact to the Mantik Registry.
   * @param mantikArtifact the Artifact
   * @param payload the payload source (if there is any).
   * @return remote representation
   */
  def addMantikArtifact(mantikArtifact: MantikArtifact, payload: Option[PayloadSource]): Future[MantikArtifact]

  /**
   * Ensure that the given item is referenced by mantikId.
   * @return true if the item was found and updated, false if already existant.
   */
  def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean]
}

object MantikRegistry {
  /** A source for content type and payload */
  type PayloadSource = (String, Source[ByteString, _])
}

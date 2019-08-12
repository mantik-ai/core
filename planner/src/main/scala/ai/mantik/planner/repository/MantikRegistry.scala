package ai.mantik.planner.repository

import ai.mantik.componently.{ AkkaRuntime, Component, ComponentBase }
import ai.mantik.elements.{ ItemId, MantikId }
import ai.mantik.planner.repository.impl.MantikRegistryImpl
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/** Represents the Mantik Registry (Mantik Hub). */
@ImplementedBy(classOf[MantikRegistryImpl])
trait MantikRegistry extends Component {

  /** Retrieves an Artifact from the Mantik Registry. */
  def get(mantikId: MantikId): Future[MantikArtifact]

  /** A source for content type and payload */
  type PayloadSource = (String, Source[ByteString, _])

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
  def ensureMantikId(itemId: ItemId, mantikId: MantikId): Future[Boolean]
}

object MantikRegistry {

  /** Returns an empty no-op registry. */
  def empty(implicit akkaRuntime: AkkaRuntime): MantikRegistry = new ComponentBase with MantikRegistry {
    private val NotFound = Future.failed(new Errors.NotFoundException("Empty Registry"))

    override def get(mantikId: MantikId): Future[MantikArtifact] = NotFound

    override def ensureMantikId(itemId: ItemId, mantikId: MantikId): Future[Boolean] = NotFound

    override def getPayload(fileId: String): Future[PayloadSource] = NotFound

    override def addMantikArtifact(mantikArtifact: MantikArtifact, payload: Option[(String, Source[ByteString, _])]): Future[MantikArtifact] = {
      NotFound
    }
  }
}
package ai.mantik.planner.repository

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.{ ItemId, MantikId }
import ai.mantik.planner.repository.MantikRegistry.PayloadSource
import ai.mantik.planner.repository.impl.MantikRegistryImpl
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/** A Remote Mantik Registry (Mantik Hub) */
@ImplementedBy(classOf[MantikRegistryImpl])
trait RemoteMantikRegistry extends MantikRegistry {

}

object RemoteMantikRegistry {

  /** Returns an empty no-op registry. */
  def empty(implicit akkaRuntime: AkkaRuntime): RemoteMantikRegistry = new ComponentBase with RemoteMantikRegistry {
    private val NotFound = Future.failed(new Errors.NotFoundException("Empty Registry"))

    override def get(mantikId: MantikId): Future[MantikArtifact] = NotFound

    override def ensureMantikId(itemId: ItemId, mantikId: MantikId): Future[Boolean] = NotFound

    override def getPayload(fileId: String): Future[PayloadSource] = NotFound

    override def addMantikArtifact(mantikArtifact: MantikArtifact, payload: Option[(String, Source[ByteString, _])]): Future[MantikArtifact] = {
      NotFound
    }
  }
}
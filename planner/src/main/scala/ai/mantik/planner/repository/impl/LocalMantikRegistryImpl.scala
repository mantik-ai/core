package ai.mantik.planner.repository.impl

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.{ ItemId, MantikId, NamedMantikId }
import ai.mantik.planner.repository.{ FileRepository, LocalMantikRegistry, MantikArtifact, Repository }
import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.Inject

import scala.concurrent.Future

class LocalMantikRegistryImpl @Inject() (
    fileRepository: FileRepository,
    repository: Repository
)(
    implicit
    akkaRuntime: AkkaRuntime
) extends ComponentBase with LocalMantikRegistry {

  override def get(mantikId: MantikId): Future[MantikArtifact] = {
    repository.get(mantikId)
  }

  override def getPayload(fileId: String): Future[(String, Source[ByteString, _])] = {
    fileRepository.loadFile(fileId)
  }

  override def addMantikArtifact(mantikArtifact: MantikArtifact, payload: Option[(String, Source[ByteString, _])]): Future[MantikArtifact] = {
    val fileStorage: Future[Option[(String, Future[Unit])]] = payload.map {
      case (contentType, source) =>
        for {
          storage <- fileRepository.requestFileStorage(temporary = false)
          sink <- fileRepository.storeFile(storage.fileId, contentType)
        } yield {
          Some(storage.fileId -> source.runWith(sink))
        }
    }.getOrElse(
      Future.successful(None)
    )

    for {
      storage <- fileStorage
      updated = mantikArtifact.copy(fileId = storage.map(_._1))
      _ <- repository.store(updated)
      _ <- storage.map(_._2).getOrElse(Future.successful(())) // wait for file upload
    } yield {
      updated
    }
  }

  override def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
    repository.ensureMantikId(itemId, mantikId)
  }

  override def list(alsoAnonymous: Boolean, deployedOnly: Boolean, kindFilter: Option[String]): Future[IndexedSeq[MantikArtifact]] = {
    repository.list(
      alsoAnonymous = alsoAnonymous,
      deployedOnly = deployedOnly,
      kindFilter = kindFilter
    )
  }
}

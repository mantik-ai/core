package ai.mantik.planner.impl.exec
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.ItemId
import ai.mantik.planner.repository.{FileRepository, FileRepositoryServer, Repository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
  * Provides payload data via embedded HTTP Server (no security), useful for local docker running.
  * Note: all files are readable via HTTP.
  */
@Singleton
private[mantik] class LocalServerExecutionPayloadProvider @Inject() (
    fileRepository: FileRepository,
    repo: Repository
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with ExecutionPayloadProvider {
  case class TemporaryFileKey(
      fileId: String
  )

  private val repoServer = new FileRepositoryServer(fileRepository)
  private val serverAddress = repoServer.address()

  private def makeUrlForFile(fileId: String): String = {
    val result = s"http://${serverAddress.host}:${serverAddress.port}/files/${fileId}"
    logger.debug(s"Mapped internal file ${fileId} to ${result}")
    result
  }

  override def provideTemporary(fileId: String): Future[(TemporaryFileKey, String)] = {
    Future.successful(
      (TemporaryFileKey(fileId), makeUrlForFile(fileId))
    )
  }

  override def undoTemporary(keys: Seq[TemporaryFileKey]): Future[Unit] = {
    // Nothing to do
    Future.successful(())
  }

  override def providePermanent(itemId: ItemId): Future[Option[String]] = {
    repo.get(itemId).map { item =>
      item.fileId.map { fileId =>
        makeUrlForFile(fileId)
      }
    }
  }

  override def undoPermanent(itemId: ItemId): Future[Unit] = {
    // Nothing to do
    Future.successful(())
  }
}

package ai.mantik.planner.impl.exec

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.ItemId
import ai.mantik.executor.ExecutorFileStorage
import ai.mantik.planner.repository.{FileRepository, Repository}
import akka.stream.scaladsl.Keep

import java.time.temporal.{ChronoUnit, UnsupportedTemporalTypeException}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{Future, duration}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

/** Provides payload using  [[ExecutorFileStorage]] */
@Singleton
private[mantik] class ExecutorStorageExecutionPayloadProvider @Inject() (
    fileRepository: FileRepository,
    repo: Repository,
    storage: ExecutorFileStorage
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with ExecutionPayloadProvider {

  /** Duration how long temporary files are shared for executor nodes */
  val temporaryStorageTimeout: FiniteDuration = temporalConfig("mantik.planner.temporaryFileShareDuration")

  private def temporalConfig(key: String): FiniteDuration = {
    val amount = config.getTemporal(key)
    try {
      FiniteDuration(amount.get(ChronoUnit.SECONDS), duration.SECONDS)
    } catch {
      case _: UnsupportedTemporalTypeException =>
        FiniteDuration(amount.get(ChronoUnit.DAYS), duration.DAYS)
    }
  }

  /** What we've done with a temporary file. */
  case class TemporaryFileKey(
      uploaded: Boolean,
      remoteFileId: String
  )

  override def provideTemporary(fileId: String): Future[(TemporaryFileKey, String)] = {
    // Check if already deployed
    repo.byFileId(fileId).flatMap { existingArtifacts =>
      val remoteFileId = existingArtifacts.collectFirst {
        case a if a.executorStorageId.isDefined => a.executorStorageId.get
      }
      remoteFileId match {
        case None =>
          // We have to upload the file
          uploadFile(fileId).flatMap { remoteFileId =>
            shareTemporaryFile(remoteFileId)
              .map { url =>
                TemporaryFileKey(uploaded = true, remoteFileId) -> url
              }
              .recoverWith { case NonFatal(e) =>
                logger.warn(s"Sharing of file ${remoteFileId} failed, deleting it again")
                storage.deleteFile(remoteFileId)
                Future.failed(e)
              }
          }
        case Some(remoteFileId) =>
          // File is already deployed, let's use that.
          shareTemporaryFile(remoteFileId).map { url =>
            TemporaryFileKey(uploaded = false, remoteFileId = remoteFileId) -> url
          }
      }
    }
  }

  /** Upload file from local repository */
  private def uploadFile(fileId: String): Future[String] = {
    val remoteFileId = fileId // just use the same
    logger.debug(s"Uploading file ${fileId} --> ${remoteFileId}")
    val t0 = System.currentTimeMillis()
    for {
      loadResult <- fileRepository.loadFile(fileId)
      uploadRequestResult <- storage.storeFile(remoteFileId, loadResult.fileSize)
      uploadResult <- loadResult.source.toMat(uploadRequestResult.sink)(Keep.right).run()
    } yield {
      val t1 = System.currentTimeMillis()
      logger.debug(s"Uploading file ${fileId} --> ${remoteFileId}, done (${t1 - t0}ms), ${uploadResult.bytes}")
      remoteFileId
    }
  }

  /** Shares file, returns URL */
  private def shareTemporaryFile(remoteFileId: String): Future[String] = {
    storage
      .shareFile(remoteFileId, temporaryStorageTimeout)
      .map(_.url)
  }

  override def undoTemporary(keys: Seq[TemporaryFileKey]): Future[Unit] = {
    val futures = keys.map { key =>
      if (key.uploaded) {
        logger.debug(s"Deleting temporary file ${key.remoteFileId}")
        storage.deleteFile(key.remoteFileId).recover { case NonFatal(e) =>
          // Now it's up to the user to delete that file :(
          logger.error(s"Could not delete remote temporary file ${key.remoteFileId}", e)
          Future.successful(())
        }
      } else {
        Future.successful(())
      }
    }
    Future.sequence(futures).map { _ =>
      ()
    }
  }

  override def providePermanent(itemId: ItemId): Future[Option[String]] = {
    logger.debug(s"Permanently sharing ${itemId}")
    repo.get(itemId).flatMap { item =>
      item.fileId match {
        case None => Future.successful(None)
        case Some(fileId) =>
          val remoteFileIdFuture: Future[String] = item.executorStorageId match {
            case None =>
              uploadPermanentForArtifact(itemId, fileId)
            case Some(remoteFileId) =>
              Future.successful(remoteFileId)
          }
          remoteFileIdFuture.flatMap { remoteFileId =>
            logger.debug(s"Permanently shared ${itemId} on remote file ${remoteFileId}")
            providePermanentUrlForRemoteFileId(itemId, remoteFileId).map(Some(_))
          }
      }
    }
  }

  private def uploadPermanentForArtifact(itemId: ItemId, fileId: String): Future[String] = {
    for {
      remoteFileId <- uploadFile(fileId)
      _ <- repo.updateExecutorStorageId(itemId, Some(remoteFileId))
    } yield {
      remoteFileId
    }
  }

  private def providePermanentUrlForRemoteFileId(itemId: ItemId, remoteFileId: String): Future[String] = {
    storage.setAcl(remoteFileId, true).map { result =>
      result.url
    }
  }

  override def undoPermanent(itemId: ItemId): Future[Unit] = {
    for {
      artifact <- repo.get(itemId)
      remoteFileId = artifact.executorStorageId
      _ <- remoteFileId match {
        case None => Future.successful(None)
        case Some(remoteFileId) =>
          deleteUploaded(itemId, remoteFileId)
      }
    } yield {
      ()
    }
  }

  private def deleteUploaded(itemId: ItemId, remoteFileId: String): Future[Unit] = {
    logger.debug(s"Deleting remote file id ${remoteFileId}")
    for {
      _ <- storage.deleteFile(remoteFileId)
      _ <- repo.updateExecutorStorageId(itemId, None)
    } yield (())
  }
}

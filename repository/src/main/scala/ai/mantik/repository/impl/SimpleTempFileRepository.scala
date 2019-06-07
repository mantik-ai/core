package ai.mantik.repository.impl

import java.nio.file.{ Files, Path }

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import akka.util.ByteString
import ai.mantik.repository.FileRepository

import scala.collection.mutable
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.Success
import ai.mantik.repository.Errors
import com.typesafe.config.Config
import org.apache.commons.io.FileUtils

/** Simple local file service, for development, no security at all. */
class SimpleTempFileRepository(config: Config)(implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext)
  extends FileRepositoryServer(config) {

  val directory = Files.createTempDirectory("mantik_simple_storage")

  // Note: the file service must be enabled in kubernetes.
  // inside the Mantik namespace

  case class FileInfo(temporary: Boolean, written: Option[Long] = None, contentType: Option[String] = None)

  object lock
  val files = mutable.Map.empty[String, FileInfo]
  var nextId = 1

  private def fileStatus(id: String): Option[FileInfo] = {
    lock.synchronized {
      return files.get(id)
    }
  }

  private def setFileStatus(id: String, f: FileInfo => FileInfo): Unit = {
    lock.synchronized {
      files.get(id).map(f).foreach { updated =>
        files.put(id, updated)
      }

    }
  }

  override def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult] = {
    lock.synchronized {
      val id = nextId.toString
      nextId += 1
      files(id) = FileInfo(temporary = temporary)
      Future.successful(
        FileRepository.FileStorageResult(
          id, makePath(id)
        )
      )
    }
  }

  override def requestFileGet(id: String, optimistic: Boolean): Future[FileRepository.FileGetResult] = {
    fileStatus(id) match {
      case None => Future.failed(new Errors.NotFoundException(s"File ${id} not found"))
      case Some(file) if !optimistic && file.written.isEmpty => Future.failed(new Errors.NotFoundException(s"File ${id} ha no content yet"))
      case Some(file) =>
        Future.successful(
          FileRepository.FileGetResult(
            id, makePath(id), contentType = file.contentType
          )
        )
    }
  }

  override def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Unit]]] = {
    fileStatus(id) match {
      case None => return Future.failed(new Errors.NotFoundException(s"File ${id} not found"))
      case Some(file) =>
        val resolved = localFileName(id)
        val sink = FileIO.toPath(resolved).mapMaterializedValue { ioResult =>
          ioResult.andThen {
            case Success(s) =>
              logger.info(s"Wrote ${resolved} with ${s.count} bytes")
              setFileStatus(id, _.copy(written = Some(s.count), contentType = Some(contentType)))
          }.map(_ => ())
        }
        Future.successful(
          sink
        )
    }
  }

  override def loadFile(id: String): Future[Source[ByteString, _]] = {
    fileStatus(id) match {
      case None => Future.failed(new Errors.NotFoundException(s"File ${id} not found"))
      case Some(file) if file.written.isEmpty =>
        Future.failed(new Errors.NotFoundException(s"File ${id} ha no content yet"))
      case Some(file) =>
        val resolved = localFileName(id)
        Future.successful(FileIO.fromPath(resolved))
    }
  }

  private def localFileName(id: String): Path = {
    val resolved = directory.resolve(id)
    if (!resolved.startsWith(directory)) {
      // security breach
      throw new Errors.NotFoundException(s"ID may not lead out of storage directory")
    }
    resolved
  }

  override def deleteFile(id: String): Future[Boolean] = {
    val result = lock.synchronized {
      files.get(id) match {
        case None => false
        case Some(status) =>
          Files.deleteIfExists(localFileName(id))
          files.remove(id)
          true
      }
    }
    Future.successful(result)
  }

  override def shutdown(): Unit = {
    super.shutdown()
    FileUtils.deleteDirectory(directory.toFile)
  }
}

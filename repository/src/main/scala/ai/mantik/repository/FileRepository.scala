package ai.mantik.repository

import java.net.InetSocketAddress

import ai.mantik.repository.impl.Factory
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }

/** Responsible for File Storage. */
trait FileRepository {

  /** Request the storage of a new file. */
  def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult]

  /**
   * Request the loading of a file.
   * @param optimistic if true, the file handle will also be returned, if the file is not yet existant.
   */
  def requestFileGet(id: String, optimistic: Boolean = false): Future[FileRepository.FileGetResult]

  /** Request storing a file (must be requested at first). */
  def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Unit]]]

  /** Delete a file. Returns true, if the file existed. */
  def deleteFile(id: String): Future[Boolean]

  /** Request retrieval of a file. */
  def loadFile(id: String): Future[Source[ByteString, _]]

  /** Returns the address of the repository (must be reachable from the executor). */
  def address(): InetSocketAddress

  /** Shutdown the repository. */
  def shutdown(): Unit = {}
}

object FileRepository {

  /** Result of file storage request. */
  case class FileStorageResult(
      fileId: String,
      // Relative Path under which the file is available from the server
      path: String
  )

  /** Result of get file request. */
  case class FileGetResult(
      fileId: String,
      // Relative Path under which the file is available from the server
      path: String,
      contentType: Option[String]
  )

  /** Content Type for Mantik Bundles. */
  val MantikBundleContentType = "application/x-mantik-bundle"

  /**
   * Create the FileRepository which is referenced in the config.
   * In Future this should be done using DI Ticket #86.
   */
  def createFileRepository(config: Config)(implicit actorySystem: ActorSystem, materializer: Materializer, ec: ExecutionContext): FileRepository = {
    Factory.createFileRepository(config)
  }
}
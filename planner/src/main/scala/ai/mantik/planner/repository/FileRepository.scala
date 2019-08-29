package ai.mantik.planner.repository

import ai.mantik.componently.Component
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future

/** Responsible for File Storage. */
private[mantik] trait FileRepository extends Component {

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
  def loadFile(id: String): Future[(String, Source[ByteString, _])]
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
   * Returns the path, under which the FileRepositoryServer serves files of a fileId.
   * Do not change, without changing the server.
   */
  def makePath(id: String): String = {
    "files/" + id
  }
}
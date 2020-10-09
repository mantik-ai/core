package ai.mantik.planner.repository

import ai.mantik.componently.Component
import ai.mantik.elements.errors.{ ErrorCode, ErrorCodes, MantikException }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import io.grpc.Status.Code

import scala.concurrent.Future

/** Responsible for File Storage. */
private[mantik] trait FileRepository extends Component {

  /** Request the storage of a new file. */
  def requestFileStorage(contentType: String, temporary: Boolean): Future[FileRepository.FileStorageResult]

  /**
   * Request the loading of a file.
   * @param optimistic if true, the file handle will also be returned, if the file is not yet existant.
   */
  def requestFileGet(id: String, optimistic: Boolean = false): Future[FileRepository.FileGetResult]

  /** Request storing a file (must be requested at first). */
  def storeFile(id: String): Future[Sink[ByteString, Future[Long]]]

  /** Delete a file. Returns true, if the file existed. */
  def deleteFile(id: String): Future[Boolean]

  /**
   * Request retrieval of a file.
   * @return content type and file source
   */
  def loadFile(id: String): Future[(String, Source[ByteString, _])]

  /** Request copying a file. */
  def copy(from: String, to: String): Future[Unit]
}

object FileRepository {

  /** A File was not found. */
  val NotFoundCode = ErrorCodes.RootCode.derive(
    "NotFound",
    grpcCode = Some(Code.NOT_FOUND)
  )

  /** Some operation can't be handled due invalid content type */
  val InvalidContentType = ErrorCodes.RootCode.derive(
    "InvalidContentType",
    grpcCode = Some(Code.INVALID_ARGUMENT)
  )

  /** Result of file storage request. */
  case class FileStorageResult(
      fileId: String,
      // Relative Path under which the file is available from the server
      path: String
  )

  /** Result of get file request. */
  case class FileGetResult(
      fileId: String,
      // file has been marked as being temporary
      isTemporary: Boolean,
      // Relative Path under which the file is available from the server
      path: String,
      contentType: String
  )

  /**
   * Returns the path, under which the FileRepositoryServer serves files of a fileId.
   * Do not change, without changing the server.
   */
  def makePath(id: String): String = {
    "files/" + id
  }
}
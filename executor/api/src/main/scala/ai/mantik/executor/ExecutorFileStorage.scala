package ai.mantik.executor

import ai.mantik.componently.Component
import ai.mantik.executor.ExecutorFileStorage.{DeleteResult, SetAclResult, ShareResult, StoreFileResult}
import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/** File storage which is reachable by the Executor. */
trait ExecutorFileStorage extends Component {

  /**
    * Request storage of a file.
    * Note: contentLength must be known (some systems like S3 require it) in advance
    */
  def storeFile(id: String, contentLength: Long): Future[Sink[ByteString, Future[StoreFileResult]]]

  /** Request loading of file */
  def getFile(id: String): Future[Source[ByteString, NotUsed]]

  /**
    * Delete a file.
    * Does nothing if the file is not found
    */
  def deleteFile(id: String): Future[DeleteResult]

  /** Shares a file for an executor run (short time) */
  def shareFile(id: String, duration: FiniteDuration): Future[ShareResult]

  /** Modify ACL (e.g. make file public) */
  def setAcl(id: String, public: Boolean): Future[SetAclResult]

  /** Returns the main URL of an object. The URL is accessible if the object is public-readable */
  def getUrl(id: String): String
}

object ExecutorFileStorage {

  case class StoreFileResult(
      bytes: Long
  )

  case class ShareResult(
      url: String,
      expiration: Instant
  )

  case class DeleteResult(
      // Note: some implementations may not return if the file was found
      found: Option[Boolean] = None
  )

  case class SetAclResult(
      // Note: destination URL may be different
      url: String
  )
}

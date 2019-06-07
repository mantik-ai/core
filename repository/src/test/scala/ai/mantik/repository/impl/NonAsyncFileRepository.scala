package ai.mantik.repository.impl

import ai.mantik.repository.FileRepository
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

/** Helper which converts the async API into a sync API for testcases. */
trait NonAsyncFileRepository extends FileRepository {

  def requestFileStorageSync(temp: Boolean): FileRepository.FileStorageResult = {
    await(this.requestFileStorage(temp))
  }

  def storeFileSync(id: String, contentType: String, bytes: ByteString)(implicit materializer: Materializer): Unit = {
    val sink = await(this.storeFile(id, contentType))
    await(Source.single(bytes).runWith(sink))
  }

  def requestAndStoreSync(temp: Boolean, contentType: String, bytes: ByteString)(implicit materializer: Materializer): FileRepository.FileStorageResult = {
    val storageResult = requestFileStorageSync(temp)
    storeFileSync(storageResult.fileId, contentType, bytes)
    storageResult
  }

  def getFileSync(id: String, optimistic: Boolean): FileRepository.FileGetResult = {
    await(this.requestFileGet(id, optimistic))
  }

  def getFileContentSync(id: String)(implicit materializer: Materializer): ByteString = {
    val source = await(this.loadFile(id))
    val sink = Sink.seq[ByteString]
    val byteBlobs = await(source.runWith(sink))
    byteBlobs.foldLeft(ByteString.empty)(_ ++ _)
  }

  private def await[T](f: Future[T]): T = {
    Await.result(f, 10.seconds)
  }
}

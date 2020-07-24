package ai.mantik.planner.impl.exec

import java.net.InetSocketAddress
import java.util.UUID

import ai.mantik.componently.ComponentBase
import ai.mantik.planner.repository.FileRepository
import ai.mantik.planner.repository.FileRepository.{ FileGetResult, FileStorageResult }
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.planner.{ PlanFile, PlanFileReference }
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future
import scala.language.reflectiveCalls

class ExecutionOpenFilesBuilderSpec extends TestBaseWithAkkaRuntime {

  trait Env {
    val fileCache = new FileCache()
    var nextFileId = 1

    val repo = new ComponentBase with FileRepository {

      var crashingReads = 0 // increase to let reads fail
      val getResults = List.newBuilder[FileGetResult]
      val writeResults = List.newBuilder[FileStorageResult]
      var wasOptimisticRead = false
      var wasTemporaryWrite = false

      override def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult] = {
        val id = nextFileId
        nextFileId += 1
        wasTemporaryWrite = temporary
        val result = FileRepository.FileStorageResult(id.toString, "path")
        writeResults += result
        Future.successful(
          result
        )
      }

      override def requestFileGet(id: String, optimistic: Boolean): Future[FileRepository.FileGetResult] = {
        if (crashingReads > 0) {
          crashingReads -= 1
          return Future.failed(new RuntimeException("Read failed"))
        }
        val result = FileRepository.FileGetResult(id.toString, false, "path", None)
        wasOptimisticRead = optimistic
        getResults += result
        Future.successful(
          result
        )
      }

      override def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Long]]] = ???
      override def loadFile(id: String): Future[(String, Source[ByteString, _])] = ???
      override def deleteFile(id: String): Future[Boolean] = ???

      override def copy(from: String, to: String): Future[Unit] = ???
    }

    val builder = new ExecutionOpenFilesBuilder(repo, fileCache)
  }

  it should "work for an empty case" in new Env {
    val result = await(builder.openFiles(Nil))
    result.readFiles shouldBe empty
    result.writeFiles shouldBe empty
  }

  it should "resolve pipe patterns" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true)
    )
    val result = await(builder.openFiles(files))
    result.readFiles shouldBe Map(PlanFileReference(1) -> repo.getResults.result().head)
    result.writeFiles shouldBe Map(PlanFileReference(1) -> repo.writeResults.result().head)
    repo.wasOptimisticRead shouldBe true
    repo.wasTemporaryWrite shouldBe true
    result.resolveFileId(PlanFileReference(1)) shouldBe "1"
  }

  it should "resolve read patterns" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("id1"))
    )
    val result = await(builder.openFiles(files))
    result.readFiles shouldBe Map(PlanFileReference(1) -> repo.getResults.result().head)
    result.writeFiles shouldBe empty
    repo.wasOptimisticRead shouldBe false
    result.resolveFileId(PlanFileReference(1)) shouldBe "id1"
  }

  it should "resolve writes" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), write = true)
    )
    val result = await(builder.openFiles(files))
    result.readFiles shouldBe empty
    result.writeFiles shouldBe Map(PlanFileReference(1) -> repo.writeResults.result().head)
    repo.wasTemporaryWrite shouldBe false
    result.resolveFileId(PlanFileReference(1)) shouldBe "1"
  }

  it should "resolve all together" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("xx")),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = true),
      PlanFile(PlanFileReference(3), write = true)
    )
    val result = await(builder.openFiles(files))
    result.readFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2))
    result.writeFiles.keys shouldBe Set(PlanFileReference(2), PlanFileReference(3))
    result.resolveFileId(PlanFileReference(1)) shouldBe "xx"
    result.resolveFileId(PlanFileReference(2)) shouldBe "1"
    result.resolveFileId(PlanFileReference(3)) shouldBe "2"
  }
}

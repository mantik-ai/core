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
        val result = FileRepository.FileGetResult(id.toString, "path", None)
        wasOptimisticRead = optimistic
        getResults += result
        Future.successful(
          result
        )
      }

      override def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Unit]]] = ???
      override def loadFile(id: String): Future[(String, Source[ByteString, _])] = ???
      override def deleteFile(id: String): Future[Boolean] = ???
    }

    val builder = new ExecutionOpenFilesBuilder(repo, fileCache)
  }

  it should "work for an empty case" in new Env {
    val result = await(builder.openFiles(Nil, Nil))
    result.cacheHits shouldBe empty
    result.readFiles shouldBe empty
    result.writeFiles shouldBe empty
  }

  it should "resolve pipe patterns" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true)
    )
    val result = await(builder.openFiles(Nil, files))
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
    val result = await(builder.openFiles(Nil, files))
    result.readFiles shouldBe Map(PlanFileReference(1) -> repo.getResults.result().head)
    result.writeFiles shouldBe empty
    repo.wasOptimisticRead shouldBe false
    result.resolveFileId(PlanFileReference(1)) shouldBe "id1"
  }

  it should "resolve writes" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), write = true)
    )
    val result = await(builder.openFiles(Nil, files))
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
    val result = await(builder.openFiles(Nil, files))
    result.readFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2))
    result.writeFiles.keys shouldBe Set(PlanFileReference(2), PlanFileReference(3))
    result.resolveFileId(PlanFileReference(1)) shouldBe "xx"
    result.resolveFileId(PlanFileReference(2)) shouldBe "1"
    result.resolveFileId(PlanFileReference(3)) shouldBe "2"
  }

  it should "resolve a cache group" in new Env {
    // note: Sets are unstable, however Set2 is not, so the test works (it is order dependent)
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    fileCache.add(id1, "xx1")
    fileCache.add(id2, "xx2")
    val group = List(id1, id2)
    val files = List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true, cacheKey = Some(id1)),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = true, cacheKey = Some(id2))
    )
    val result = await(builder.openFiles(List(group), files))
    result.readFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2))
    result.writeFiles shouldBe empty
    result.resolveFileId(PlanFileReference(1)) shouldBe "xx1"
    result.resolveFileId(PlanFileReference(2)) shouldBe "xx2"
    result.cacheHits shouldBe Set(group)
  }

  it should "fail if a file fails in a cache group" in new Env {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    fileCache.add(id1, "xx1")
    // id2 is missing
    val group = List(id1, id2)
    val files = List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true, cacheKey = Some(id1)),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = true, cacheKey = Some(id2))
    )
    val result = await(builder.openFiles(List(group), files))
    result.readFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2))
    result.writeFiles shouldBe Map(
      PlanFileReference(1) -> repo.writeResults.result().head,
      PlanFileReference(2) -> repo.writeResults.result()(1)
    )
    result.resolveFileId(PlanFileReference(1)) shouldBe "1"
    result.resolveFileId(PlanFileReference(2)) shouldBe "2"
    result.cacheHits shouldBe empty
  }

  it should "fail if a file could not be retrieved from a cache group" in new Env {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    fileCache.add(id1, "xx1")
    fileCache.add(id2, "xx2")
    val group = List(id1, id2)
    val files = List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true, cacheKey = Some(id1)),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = true, cacheKey = Some(id2))
    )
    repo.crashingReads = 1
    val result = await(builder.openFiles(List(group), files))
    result.readFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2))
    result.writeFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2))
    result.resolveFileId(PlanFileReference(1)) shouldBe "1"
    result.resolveFileId(PlanFileReference(2)) shouldBe "2"
    result.cacheHits shouldBe empty
  }

  it should "work in a complicated example" in new Env {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val id3 = UUID.randomUUID()
    fileCache.add(id1, "xx1")
    // id 2 is missing
    fileCache.add(id3, "xx3")

    val group1 = List(id1, id2)
    val group2 = List(id3)

    val files = List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true, cacheKey = Some(id1)),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = true, cacheKey = Some(id2)),
      PlanFile(PlanFileReference(3), read = true, write = true, temporary = true, cacheKey = Some(id3)),
      PlanFile(PlanFileReference(4), write = true),
      PlanFile(PlanFileReference(5), read = true, fileId = Some("e5"))
    )

    val result = await(builder.openFiles(List(group1, group2), files))

    result.readFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2), PlanFileReference(3), PlanFileReference(5))
    result.writeFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2), PlanFileReference(4))

    result.resolveFileId(PlanFileReference(1)) shouldBe "1"
    result.resolveFileId(PlanFileReference(2)) shouldBe "2"
    result.resolveFileId(PlanFileReference(3)) shouldBe "xx3"
    result.resolveFileId(PlanFileReference(4)) shouldBe "3"
    result.resolveFileId(PlanFileReference(5)) shouldBe "e5"

    result.cacheHits shouldBe Set(group2)
  }
}

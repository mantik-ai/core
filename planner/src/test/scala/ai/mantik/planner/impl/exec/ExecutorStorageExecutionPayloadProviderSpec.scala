/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.planner.impl.exec

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ds.{DataType, FundamentalType}
import ai.mantik.elements.{DataSetDefinition, ItemId, MantikDefinition, MantikHeader}
import ai.mantik.executor.{Errors, ExecutorFileStorage}
import ai.mantik.planner.repository.{ContentTypes, MantikArtifact}
import ai.mantik.planner.repository.impl.{LocalFileRepository, LocalRepository, NonAsyncFileRepository}
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.{TempDirSupport, TestBase}
import akka.NotUsed
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString

import java.time.temporal.ChronoUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/** Dummy in memory storage */
class DummyStorage(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with ExecutorFileStorage {
  case class Entry(
      length: Long,
      data: Option[ByteString] = None,
      shared: Option[FiniteDuration] = None,
      isPublic: Boolean = false
  )
  private var entries: Map[String, Entry] = Map.empty
  private var _uploadCount: Int = 0

  def uploadCount: Int = _uploadCount

  private def updateEntry(id: String)(f: Entry => Entry): Option[Entry] = {
    entries.get(id) match {
      case None => // nothing
        None
      case Some(entry) =>
        val updated = f(entry)
        entries += (id -> updated)
        Some(updated)
    }
  }

  def getEntry(id: String): Option[Entry] = entries.get(id)

  override def storeFile(
      id: String,
      contentLength: Long
  ): Future[Sink[ByteString, Future[ExecutorFileStorage.StoreFileResult]]] = {
    entries += (id -> Entry(contentLength))
    Future.successful {
      val sink = Sink.seq[ByteString]
      sink.mapMaterializedValue { data =>
        data.map { entries =>
          val allData = entries.foldLeft(ByteString.empty)(_ ++ _)
          updateEntry(id)(_.copy(data = Some(allData)))
          _uploadCount += 1
          ExecutorFileStorage.StoreFileResult(allData.length)
        }
      }
    }
  }

  override def getFile(id: String): Future[Source[ByteString, NotUsed]] = {
    getEntry(id) match {
      case None                              => Future.failed(new Errors.NotFoundException(""))
      case Some(value) if value.data.isEmpty => Future.failed(new Errors.NotFoundException("No data supplied"))
      case Some(value)                       => Future.successful(Source.single(value.data.get))
    }
  }

  override def deleteFile(id: String): Future[ExecutorFileStorage.DeleteResult] = {
    getEntry(id) match {
      case None => Future.successful(ExecutorFileStorage.DeleteResult(Some(false)))
      case Some(_) => {
        entries -= (id)
        Future.successful(ExecutorFileStorage.DeleteResult(Some(true)))
      }
    }
  }

  override def shareFile(id: String, duration: FiniteDuration): Future[ExecutorFileStorage.ShareResult] = {
    updateEntry(id) { _.copy(shared = Some(duration)) }
      .map { updated =>
        val url = s"http://fake/${id}/shared"
        Future.successful(
          ExecutorFileStorage.ShareResult(url, clock.instant().plus(duration.toSeconds, ChronoUnit.SECONDS))
        )
      }
      .getOrElse {
        Future.failed(new Errors.NotFoundException(s"Entry not found"))
      }
  }

  override def setAcl(id: String, public: Boolean): Future[ExecutorFileStorage.SetAclResult] = {
    updateEntry(id) { _.copy(isPublic = public) }
      .map { updated =>
        Future.successful(ExecutorFileStorage.SetAclResult(s"http://fake/${id}/public"))
      }
      .getOrElse {
        Future.failed(new Errors.NotFoundException(s"Entry not found"))
      }
  }

  override def getUrl(id: String): String = {
    s"http://fakle/${id}"
  }
}

class ExecutorStorageExecutionPayloadProviderSpec extends TestBaseWithAkkaRuntime with TempDirSupport {
  trait Env {
    val repo = new LocalRepository(tempDirectory)
    val fileRepo = new LocalFileRepository(tempDirectory) with NonAsyncFileRepository
    val storage = new DummyStorage()

    val provider = new ExecutorStorageExecutionPayloadProvider(
      fileRepo,
      repo,
      storage
    )

    val fileId = fileRepo.requestFileStorageSync(ContentTypes.MantikBundleContentType, temp = true).fileId
    fileRepo.storeFileSync(fileId, ByteString("Hello World"))
  }

  val dummyHeader = MantikHeader
    .pure(
      DataSetDefinition("bridge1", FundamentalType.Int32)
    )
    .toJson

  "provideTemporary" should "provide a temporary file" in new Env {
    val (key, url) = await(provider.provideTemporary(fileId))
    key.uploaded shouldBe true
    key.remoteFileId shouldBe fileId // it's using the same fileId for storage.
    url shouldBe s"http://fake/${fileId}/shared"
    storage.getEntry(key.remoteFileId).get.shared shouldBe Some(provider.temporaryStorageTimeout)

    withClue("it should be undoable") {
      await(provider.undoTemporary(Nil)) // nothing
      await(provider.undoTemporary(Seq(key)))
      storage.getEntry(key.remoteFileId) shouldBe empty
    }
  }

  it should "detect already uploaded files and just shares them" in new Env {
    val storageId = "storageId"
    val storageResult = await(storage.storeFile(storageId, 100))
    await(Source.single(ByteString("Another file")).toMat(storageResult)(Keep.right).run())
    val itemId = ItemId.generate()
    await(
      repo.store(
        MantikArtifact(
          mantikHeader = dummyHeader,
          fileId = Some(fileId),
          executorStorageId = Some(storageId),
          itemId = itemId,
          namedId = None
        )
      )
    )

    val (key, url) = await(provider.provideTemporary(fileId))
    key.remoteFileId shouldBe storageId
    key.uploaded shouldBe false
    url shouldBe s"http://fake/${storageId}/shared"
    storage.getEntry(storageId).get.shared shouldBe Some(provider.temporaryStorageTimeout)

    withClue("Undoing shold not delete them") {
      await(provider.undoTemporary(Seq(key)))
      storage.getEntry(storageId) shouldBe defined
    }
  }

  "providePermanent" should "provide a permanent file" in new Env {
    val itemId = ItemId.generate()
    await(
      repo.store(
        MantikArtifact(
          mantikHeader = dummyHeader,
          fileId = Some(fileId),
          executorStorageId = None,
          itemId = itemId,
          namedId = None
        )
      )
    )

    val url = await(provider.providePermanent(itemId))

    val artifactAgain = await(repo.get(itemId))
    artifactAgain.executorStorageId shouldBe defined
    url shouldBe Some(s"http://fake/${artifactAgain.executorStorageId.get}/public")

    val file = storage.getEntry(artifactAgain.executorStorageId.get)
    file.get.isPublic shouldBe true

    storage.uploadCount shouldBe 1

    withClue("it should reuse already uploaded files") {
      val url2 = await(provider.providePermanent(itemId))
      url2 shouldBe url
      storage.uploadCount shouldBe 1
    }

    withClue("It should be undoable") {
      await(provider.undoPermanent(itemId))
      val artifact2 = await(repo.get(itemId))
      artifact2.executorStorageId shouldBe None
      storage.getEntry(artifactAgain.executorStorageId.get) shouldBe None
    }
  }

  it should "do nothing if there is no pauload" in new Env {
    val itemId = ItemId.generate()
    await(
      repo.store(
        MantikArtifact(
          mantikHeader = dummyHeader,
          fileId = None,
          executorStorageId = None,
          itemId = itemId,
          namedId = None
        )
      )
    )

    val url = await(provider.providePermanent(itemId))
    url shouldBe empty
  }
}

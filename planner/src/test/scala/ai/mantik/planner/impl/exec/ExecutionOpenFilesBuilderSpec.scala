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

import ai.mantik.componently.ComponentBase
import ai.mantik.planner.repository.{ContentTypes, FileRepository}
import ai.mantik.planner.repository.FileRepository.{FileGetResult, FileStorageResult}
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.planner.{PlanFile, PlanFileReference}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.language.reflectiveCalls

class ExecutionOpenFilesBuilderSpec extends TestBaseWithAkkaRuntime {

  trait Env {
    var nextFileId = 1

    val repo = new ComponentBase with FileRepository {

      val getResults = List.newBuilder[FileGetResult]
      val writeResults = List.newBuilder[FileStorageResult]
      var wasOptimisticRead = false
      var wasTemporaryWrite = false

      override def requestFileStorage(
          contentType: String,
          temporary: Boolean
      ): Future[FileRepository.FileStorageResult] = {
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
        val result = FileRepository.FileGetResult(id.toString, false, "path", "ContentType", Some(123))
        wasOptimisticRead = optimistic
        getResults += result
        Future.successful(
          result
        )
      }

      override def storeFile(id: String): Future[Sink[ByteString, Future[Long]]] = ???
      override def loadFile(id: String): Future[FileRepository.LoadFileResult] = ???
      override def deleteFile(id: String): Future[Boolean] = ???

      override def copy(from: String, to: String): Future[Unit] = ???
    }

    val builder = new ExecutionOpenFilesBuilder(repo)
  }

  it should "work for an empty case" in new Env {
    val result = await(builder.openFiles(Nil))
    result.readFiles shouldBe empty
    result.writeFiles shouldBe empty
  }

  it should "resolve pipe patterns" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true)
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
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, fileId = Some("id1"))
    )
    val result = await(builder.openFiles(files))
    result.readFiles shouldBe Map(PlanFileReference(1) -> repo.getResults.result().head)
    result.writeFiles shouldBe empty
    repo.wasOptimisticRead shouldBe false
    result.resolveFileId(PlanFileReference(1)) shouldBe "id1"
  }

  it should "resolve writes" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, write = true)
    )
    val result = await(builder.openFiles(files))
    result.readFiles shouldBe empty
    result.writeFiles shouldBe Map(PlanFileReference(1) -> repo.writeResults.result().head)
    repo.wasTemporaryWrite shouldBe false
    result.resolveFileId(PlanFileReference(1)) shouldBe "1"
  }

  it should "resolve all together" in new Env {
    val files = List(
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, fileId = Some("xx")),
      PlanFile(PlanFileReference(2), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true),
      PlanFile(PlanFileReference(3), ContentTypes.MantikBundleContentType, write = true)
    )
    val result = await(builder.openFiles(files))
    result.readFiles.keys shouldBe Set(PlanFileReference(1), PlanFileReference(2))
    result.writeFiles.keys shouldBe Set(PlanFileReference(2), PlanFileReference(3))
    result.resolveFileId(PlanFileReference(1)) shouldBe "xx"
    result.resolveFileId(PlanFileReference(2)) shouldBe "1"
    result.resolveFileId(PlanFileReference(3)) shouldBe "2"
  }
}

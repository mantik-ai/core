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
package ai.mantik.planner.repository.impl

import ai.mantik.elements.errors.MantikException
import ai.mantik.planner.repository.{ContentTypes, FileRepository}

import scala.annotation.nowarn
import scala.concurrent.duration._

class LocalFileRepositorySpec extends FileRepositorySpecBase {

  override type RepoType = LocalFileRepository with NonAsyncFileRepository

  protected def createRepo(): RepoType = {
    new LocalFileRepository(tempDirectory) with NonAsyncFileRepository
  }

  @nowarn
  trait Env {
    val repo = new LocalFileRepository(tempDirectory) with NonAsyncFileRepository
  }

  it should "parse timeout config values" in new Env {
    repo.cleanupInterval shouldBe 1.hours
    repo.cleanupTimeout shouldBe 48.hours
    repo.timeoutScheduler.isCancelled shouldBe false
  }

  it should "disable the scheduler on shutdown" in new Env {
    repo.timeoutScheduler.isCancelled shouldBe false
    akkaRuntime.shutdown()
    repo.timeoutScheduler.isCancelled shouldBe true
  }

  "listFiles" should "work" in new Env {
    val req1 = repo.requestFileStorageSync(ContentTypes.MantikBundleContentType, true)
    val req2 = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
    val req3 = repo.requestAndStoreSync(false, ContentTypes.MantikBundleContentType, testBytes)
    repo.listFiles().toIndexedSeq should contain theSameElementsAs Seq(req1.fileId, req2.fileId, req3.fileId)
  }

  "automatic cleanup" should "automatically clean temporary files" in new Env {
    val storeResult = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
    clock.setTimeOffset(repo.cleanupTimeout.minus(1.seconds))
    repo.removeTimeoutedFiles()
    repo.getFileContentSync(storeResult.fileId) shouldBe (ContentTypes.MantikBundleContentType -> testBytes)

    clock.setTimeOffset(repo.cleanupTimeout.plus(1.seconds))
    repo.removeTimeoutedFiles()
    intercept[MantikException] {
      repo.getFileContentSync(storeResult.fileId)
    }.code.isA(FileRepository.NotFoundCode) shouldBe true
  }

  it should "automatically clean up files without content" in new Env {
    val storeResult = await(repo.requestFileStorage("ContentType", true))
    clock.setTimeOffset(repo.cleanupTimeout.minus(1.seconds))
    repo.removeTimeoutedFiles()

    repo.listFiles().toSet should contain(storeResult.fileId)

    clock.setTimeOffset(repo.cleanupTimeout.plus(1.seconds))
    repo.removeTimeoutedFiles()
    repo.listFiles().toSet should not(contain(storeResult.fileId))

    awaitException[MantikException] {
      repo.storeFile(storeResult.fileId)
    }.code.isA(FileRepository.NotFoundCode) shouldBe true
  }

  it should "not remove non-temporary files" in new Env {
    val storeResult = repo.requestAndStoreSync(false, ContentTypes.MantikBundleContentType, testBytes)
    clock.setTimeOffset(repo.cleanupTimeout.plus(1.hour))
    repo.removeTimeoutedFiles()
    repo.getFileContentSync(storeResult.fileId) shouldBe (ContentTypes.MantikBundleContentType -> testBytes)
    repo.listFiles().toSet should contain(storeResult.fileId)
  }
}

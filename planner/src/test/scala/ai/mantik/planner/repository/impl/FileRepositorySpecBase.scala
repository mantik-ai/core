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

import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.planner.repository.{ContentTypes, FileRepository}
import ai.mantik.planner.util.{ErrorCodeTestUtils, TestBaseWithAkkaRuntime}
import ai.mantik.testutils.TempDirSupport
import akka.util.ByteString

import scala.util.Random

abstract class FileRepositorySpecBase extends TestBaseWithAkkaRuntime with TempDirSupport with ErrorCodeTestUtils {

  type RepoType <: FileRepository with NonAsyncFileRepository

  protected def createRepo(): RepoType

  trait Env {
    val repo = createRepo()
  }

  protected val testBytes = ByteString {
    val bytes = new Array[Byte](1000)
    Random.nextBytes(bytes)
    bytes
  }

  it should "save and load a file" in new Env {
    val info = await(repo.requestFileStorage(ContentTypes.MantikBundleContentType, false))
    val bytesWritten = repo.storeFileSync(info.fileId, testBytes)
    bytesWritten shouldBe testBytes.length
    val get = repo.getFileSync(info.fileId, false)
    get.isTemporary shouldBe false
    get.fileSize shouldBe Some(testBytes.length)
    val (contentType, bytesAgain) = repo.getFileContentSync(info.fileId)
    contentType shouldBe ContentTypes.MantikBundleContentType
    bytesAgain shouldBe testBytes

    withClue("copy should work") {
      val store2 = await(repo.requestFileStorage(ContentTypes.MantikBundleContentType, false))
      await(repo.copy(info.fileId, store2.fileId))

      val (contentType, bytesAgain) = repo.getFileContentSync(store2.fileId)
      contentType shouldBe ContentTypes.MantikBundleContentType
      bytesAgain shouldBe testBytes
    }

    withClue("it should fail if copy destination has the wrong content type") {
      val store2 = await(repo.requestFileStorage("Other", false))
      awaitErrorCode(FileRepository.InvalidContentType) {
        repo.copy(info.fileId, store2.fileId)
      }

      interceptErrorCode(FileRepository.NotFoundCode) {
        repo.getFileContentSync(store2.fileId)
      }
    }
  }

  it should "know optimistic storage" in new Env {
    val info = await(repo.requestFileStorage(ContentTypes.MantikBundleContentType, true))

    interceptErrorCode(FileRepository.NotFoundCode) {
      repo.getFileSync(info.fileId, optimistic = false)
    }
    val getFileResponse = withClue("No exception expected here") {
      repo.getFileSync(info.fileId, optimistic = true)
    }
    getFileResponse.isTemporary shouldBe true
    getFileResponse.fileSize shouldBe None

    // now store some content
    repo.storeFileSync(info.fileId, testBytes)

    repo.getFileContentSync(info.fileId) shouldBe (ContentTypes.MantikBundleContentType -> testBytes)
  }

  it should "allow file removal " in new Env {
    val req = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
    val result = await(repo.deleteFile(req.fileId))
    result shouldBe true
    interceptErrorCode(FileRepository.NotFoundCode) {
      repo.getFileContentSync(req.fileId)
    }
    val nonExistingResult = await(repo.deleteFile("unknown"))
    nonExistingResult shouldBe false
  }
}

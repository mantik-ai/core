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

import ai.mantik.planner.repository.FileRepository
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/** Helper which converts the async API into a sync API for testcases. */
trait NonAsyncFileRepository extends FileRepository {

  def requestFileStorageSync(contentType: String, temp: Boolean): FileRepository.FileStorageResult = {
    await(this.requestFileStorage(contentType, temp))
  }

  def storeFileSync(id: String, bytes: ByteString)(implicit materializer: Materializer): Long = {
    val sink = await(this.storeFile(id))
    await(Source.single(bytes).runWith(sink))
  }

  def requestAndStoreSync(temp: Boolean, contentType: String, bytes: ByteString)(
      implicit materializer: Materializer
  ): FileRepository.FileStorageResult = {
    val storageResult = requestFileStorageSync(contentType, temp)
    storeFileSync(storageResult.fileId, bytes)
    storageResult
  }

  def getFileSync(id: String, optimistic: Boolean): FileRepository.FileGetResult = {
    await(this.requestFileGet(id, optimistic))
  }

  def getFileContentSync(id: String)(implicit materializer: Materializer): (String, ByteString) = {
    val result = await(this.loadFile(id))
    val sink = Sink.seq[ByteString]
    val byteBlobs = await(result.source.runWith(sink))
    result.contentType -> byteBlobs.foldLeft(ByteString.empty)(_ ++ _)
  }

  private def await[T](f: Future[T]): T = {
    Await.result(f, 10.seconds)
  }
}

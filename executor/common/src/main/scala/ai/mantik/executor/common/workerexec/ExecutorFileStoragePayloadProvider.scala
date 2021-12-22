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
package ai.mantik.executor.common.workerexec

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.{ExecutorFileStorage, PayloadProvider}
import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.utils.MutableMultiMap
import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import cats.implicits._
import org.apache.commons.io.FileUtils

import javax.inject.{Inject, Singleton}

/** Implements PayloadProvider using [[ExecutorFileStorage]] */
@Singleton
class ExecutorFileStoragePayloadProvider @Inject() (executorFileStorage: ExecutorFileStorage)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase
    with PayloadProvider {

  /** Duration how long temporary files are shared for executor nodes */
  val temporaryStorageTimeout: FiniteDuration = config.getFiniteDuration("mantik.executor.temporaryFileShareDuration")

  /** Lock protecting shared. */
  private object lock

  /** Maps tag ids to random ids. Note: this is only rudimentary gatekeeping, and needs proper cleanup in intervals. */
  private var shared = new MutableMultiMap[String, String] // Maps tag Ids to random ids.

  override def provide(
      tagId: String,
      temporary: Boolean,
      data: Source[ByteString, NotUsed],
      byteCount: Long,
      contentType: String
  ): Future[String] = {
    val id = UUID.randomUUID().toString
    val humanByteCount = FileUtils.byteCountToDisplaySize(byteCount)
    logger.debug(s"Attempting to upload ${humanByteCount} to Executor Storage with id ${id}")
    val t0 = System.currentTimeMillis()

    lock.synchronized {
      shared.add(tagId, id)
    }

    val result = for {
      sink <- executorFileStorage.storeFile(id, byteCount)
      _ <- data.runWith(sink)
      shareResult <- executorFileStorage.shareFile(id, temporaryStorageTimeout)
    } yield {
      val t1 = System.currentTimeMillis()
      logger.info(s"Sharing ${humanByteCount} using id ${id} took ${t1 - t0}ms")
      shareResult.url
    }

    result.andThen { case Failure(exception) =>
      logger.warn(s"Sharing failed, removing file ${id} again", exception)
      lock.synchronized {
        shared.remove(tagId, id)
      }
      executorFileStorage.deleteFile(id)
    }
  }

  override def delete(tagId: String): Future[Unit] = {
    val files = lock.synchronized {
      shared.get(tagId).toVector
    }
    files
      .map { id =>
        executorFileStorage.deleteFile(id)
      }
      .sequence
      .map { deleted =>
        lock.synchronized {
          shared.remove(tagId)
        }
        logger.debug(s"Deleting ${deleted.size} elements for tag id ${tagId}")
      }
  }
}

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
package ai.mantik.planner.repository

import ai.mantik.componently.Component
import ai.mantik.elements.errors.{ErrorCode, ErrorCodes, MantikException}
import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.grpc.Status.Code

import scala.concurrent.{ExecutionContext, Future}

/** Responsible for File Storage. */
private[mantik] trait FileRepository extends Component {

  /** Request the storage of a new file. */
  def requestFileStorage(contentType: String, temporary: Boolean): Future[FileRepository.FileStorageResult]

  /**
    * Request the loading of a file.
    * @param optimistic if true, the file handle will also be returned, if the file is not yet existant.
    */
  def requestFileGet(id: String, optimistic: Boolean = false): Future[FileRepository.FileGetResult]

  /** Request storing a file (must be requested at first). */
  def storeFile(id: String): Future[Sink[ByteString, Future[Long]]]

  /** Delete a file. Returns true, if the file existed. */
  def deleteFile(id: String): Future[Boolean]

  /**
    * Request retrieval of a file.
    * @return content type and file source
    */
  def loadFile(id: String): Future[FileRepository.LoadFileResult]

  /** Request copying a file. */
  def copy(from: String, to: String): Future[Unit]

  /**
    * Upload a file to a new ID.
    * @param contentType the file content type
    * @param temporary if true, the file will be temporary
    * @param source the file content
    * @param contentLength if true, the content length will be validated.
    * @return file id and file length
    */
  def uploadNewFile(
      contentType: String,
      source: Source[ByteString, NotUsed],
      temporary: Boolean,
      contentLength: Option[Long] = None
  ): Future[(String, Long)] = {
    import ai.mantik.componently.AkkaHelper._
    for {
      storageResult <- requestFileStorage(contentType, temporary)
      sink <- storeFile(storageResult.fileId)
      bytesWritten <- source.runWith(sink)
      _ <-
        if (contentLength.exists(_ != bytesWritten)) {
          logger.warn(
            s"Expected ${contentLength.get} bytes, but got ${bytesWritten}. Deleting file ${storageResult.fileId} again"
          )
          deleteFile(storageResult.fileId).flatMap { _ =>
            Future.failed(ErrorCodes.BadFileSize.toException(s"Expected ${contentLength.get}, got ${bytesWritten}"))
          }
        } else {
          Future.successful(())
        }
    } yield {
      (storageResult.fileId, bytesWritten)
    }
  }
}

object FileRepository {

  /** A File was not found. */
  val NotFoundCode = ErrorCodes.RootCode.derive(
    "NotFound",
    grpcCode = Some(Code.NOT_FOUND)
  )

  /** Some operation can't be handled due invalid content type */
  val InvalidContentType = ErrorCodes.RootCode.derive(
    "InvalidContentType",
    grpcCode = Some(Code.INVALID_ARGUMENT)
  )

  /** Result of file storage request. */
  case class FileStorageResult(
      fileId: String,
      // Relative Path under which the file is available from the server
      path: String
  )

  /** Result of file load request. */
  case class LoadFileResult(
      fileSize: Long,
      contentType: String,
      source: Source[ByteString, NotUsed]
  )

  /** Result of get file request. */
  case class FileGetResult(
      fileId: String,
      // file has been marked as being temporary
      isTemporary: Boolean,
      // Relative Path under which the file is available from the server
      path: String,
      contentType: String,
      // File size if known
      fileSize: Option[Long]
  )

  /**
    * Returns the path, under which the FileRepositoryServer serves files of a fileId.
    * Do not change, without changing the server.
    */
  def makePath(id: String): String = {
    "files/" + id
  }
}

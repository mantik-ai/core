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

import ai.mantik.componently.utils.FutureHelper
import ai.mantik.planner.PlanFile
import ai.mantik.planner.repository.FileRepository
import ai.mantik.planner.repository.FileRepository.{FileGetResult, FileStorageResult}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

/** Generates [[ExecutionOpenFiles]]. */
private[impl] class ExecutionOpenFilesBuilder(
    fileRepository: FileRepository
)(implicit ex: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * Open multiple files.
    * Note: files are opened sequential, as they may refer to each other.
    *
    * @param files the files to open.
    */
  def openFiles(files: List[PlanFile]): Future[ExecutionOpenFiles] = {

    val initialState = ExecutionOpenFiles()

    for {
      finalState <- FutureHelper.afterEachOtherStateful(files, initialState) { case (state, file) =>
        openFile(state, file)
      }
    } yield {
      finalState
    }
  }

  private def openFile(state: ExecutionOpenFiles, file: PlanFile): Future[ExecutionOpenFiles] = {
    if (file.cacheItemId.isDefined) {
      if (state.readFiles.contains(file.ref)) {
        // already opened from caching
        return Future.successful(state)
      }
    }
    // File write has precedence, as we have scenarios were we read and then write.
    val fileWrite: Future[Option[FileStorageResult]] = if (file.write) {
      require(file.fileId.isEmpty, "Overwriting existing files not yet supported")
      fileRepository.requestFileStorage(file.contentType, file.temporary).map(Some(_))
    } else Future.successful(None)

    val fileRead: Future[Option[FileGetResult]] = if (file.read) {
      if (file.write) {
        // wait for writing command
        fileWrite.flatMap {
          case Some(writeResponse) =>
            fileRepository.requestFileGet(writeResponse.fileId, optimistic = true).map(Some(_))
          case None =>
            throw new IllegalStateException(
              "Implementation problem: there is a read from a file, which should be written"
            )
        }
      } else {
        file.fileId match {
          case Some(id) => fileRepository.requestFileGet(id).map(Some(_))
          case None =>
            throw new IllegalArgumentException(s"Got a file read without id and without partner write (pipe)")
        }
      }
    } else {
      Future.successful(None)
    }

    for {
      writeResult <- fileWrite
      readResult <- fileRead
    } yield {
      val newWriteFiles = writeResult
        .map { writeResult =>
          state.writeFiles + (file.ref -> writeResult)
        }
        .getOrElse(state.writeFiles)

      val newReadFiles = readResult
        .map { readResult =>
          state.readFiles + (file.ref -> readResult)
        }
        .getOrElse(state.readFiles)

      state.copy(
        writeFiles = newWriteFiles,
        readFiles = newReadFiles
      )
    }
  }
}

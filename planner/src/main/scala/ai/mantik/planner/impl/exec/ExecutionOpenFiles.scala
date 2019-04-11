package ai.mantik.planner.impl.exec

import ai.mantik.planner.PlanExecutor.InvalidPlanException
import ai.mantik.planner.impl.FutureHelper
import ai.mantik.planner.{ PlanFile, PlanFileReference }
import ai.mantik.repository.FileRepository
import ai.mantik.repository.FileRepository.{ FileGetResult, FileStorageResult }

import scala.concurrent.{ ExecutionContext, Future }

/** Handles Open Files during Plan Execution, part of [[PlanExecutorImpl]]. */
private[impl] case class ExecutionOpenFiles(
    private val readFiles: Map[PlanFileReference, FileGetResult] = Map.empty,
    private val writeFiles: Map[PlanFileReference, FileStorageResult] = Map.empty
) {

  lazy val fileIds: Map[PlanFileReference, String] = {
    readFiles.mapValues(_.fileId) ++ writeFiles.mapValues(_.fileId)
  }

  def resolveFileWrite(fileReference: PlanFileReference): FileStorageResult = {
    writeFiles.getOrElse(fileReference, throw new InvalidPlanException(s"File to write $fileReference is not opened"))
  }

  def resolveFileRead(fileReference: PlanFileReference): FileGetResult = {
    readFiles.getOrElse(fileReference, throw new InvalidPlanException(s"File to read $fileReference is not opened"))
  }

  def resolveFileId(fileReference: PlanFileReference): String = {
    fileIds.getOrElse(fileReference, throw new InvalidPlanException(s"File $fileReference has no file id associated"))
  }

}

object ExecutionOpenFiles {

  /**
   * Open multiple files.
   * Note: files are opened sequential, as they may refer to each other.
   */
  def openFiles(fileRepository: FileRepository, files: List[PlanFile])(implicit ec: ExecutionContext): Future[ExecutionOpenFiles] = {
    // we open them sequentially as they can refer to each other...
    FutureHelper.afterEachOtherStateful(files, ExecutionOpenFiles()) {
      case (state, file) =>
        openFile(fileRepository, state, file)
    }
  }

  /** Open a single file. */
  def openFile(fileRepository: FileRepository, state: ExecutionOpenFiles, file: PlanFile)(implicit ec: ExecutionContext): Future[ExecutionOpenFiles] = {
    // File write has precedence, as we have scenarios were we read and then write.
    val fileWrite: Future[Option[FileStorageResult]] = if (file.write) {
      require(file.fileId.isEmpty, "Overwriting existing files not yet supported")
      fileRepository.requestFileStorage(file.temporary).map(Some(_))
    } else Future.successful(None)

    val fileRead: Future[Option[FileGetResult]] = if (file.read) {
      if (file.write) {
        // wait for writing command
        fileWrite.flatMap {
          case Some(writeResponse) =>
            fileRepository.requestFileGet(writeResponse.fileId, optimistic = true).map(Some(_))
          case None =>
            throw new IllegalStateException("Implementation problem: there is a read from a file, which should be written")
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
      val newWriteFiles = writeResult.map { writeResult =>
        state.writeFiles + (file.id -> writeResult)
      }.getOrElse(state.writeFiles)

      val newReadFiles = readResult.map { readResult =>
        state.readFiles + (file.id -> readResult)
      }.getOrElse(state.readFiles)

      state.copy(
        writeFiles = newWriteFiles,
        readFiles = newReadFiles
      )
    }
  }

}

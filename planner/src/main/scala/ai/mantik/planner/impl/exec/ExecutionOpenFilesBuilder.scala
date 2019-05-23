package ai.mantik.planner.impl.exec

import ai.mantik.planner.{ CacheKey, CacheKeyGroup, PlanFile, PlanFileReference }
import ai.mantik.planner.impl.FutureHelper
import ai.mantik.repository.FileRepository
import ai.mantik.repository.FileRepository.{ FileGetResult, FileStorageResult }
import akka.http.scaladsl.model.Uri
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }

/** Generates [[ExecutionOpenFiles]]. */
private[impl] class ExecutionOpenFilesBuilder(
    fileRepository: FileRepository,
    fileCache: FileCache
)(implicit ex: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Open multiple files.
   * Note: files are opened sequential, as they may refer to each other.
   *
   * @param cacheGroups cache groups, files are tried to be read at first
   * @param files the files to open.
   */
  def openFiles(cacheGroups: List[CacheKeyGroup], files: List[PlanFile]): Future[ExecutionOpenFiles] = {
    val initialState = ExecutionOpenFiles()

    for {
      stateAfterCache <- openCacheFiles(initialState, cacheGroups, files)
      finalState <- FutureHelper.afterEachOtherStateful(files, stateAfterCache) {
        case (state, file) =>
          openFile(state, file)
      }
    } yield {
      finalState
    }
  }

  /** Try to open all cache groups. If something within one group fails, it's skipped. */
  private def openCacheFiles(state: ExecutionOpenFiles, cacheGroups: List[CacheKeyGroup], files: List[PlanFile]): Future[ExecutionOpenFiles] = {
    val fileByCacheId: Map[CacheKey, PlanFileReference] = files.collect {
      case f if f.cacheKey.isDefined => f.cacheKey.get -> f.ref
    }.toMap

    FutureHelper.afterEachOtherStateful(cacheGroups, state) {
      case (state, cacheGroup) =>
        openCacheGroup(state, cacheGroup, fileByCacheId)
    }
  }

  /** Try to open a single cache group. If something fails, it's skipped. */
  private def openCacheGroup(state: ExecutionOpenFiles, cacheGroup: CacheKeyGroup, cacheFiles: Map[CacheKey, PlanFileReference]): Future[ExecutionOpenFiles] = {

    // The problem is, that a CacheGroup must either be opened completely or not at all
    // If we use it half, it's possible to access values which do not work together.
    // This happens e.g. on stateful non deterministic applications, like many trainings.

    Future.sequence(
      cacheGroup.view.toList.map { cacheKey =>
        openCacheFile(cacheKey, cacheFiles)
      }
    ).map { results: List[Either[String, (PlanFileReference, FileRepository.FileGetResult)]] =>
        val unflattened = unflattenEither(results)
        unflattened match {
          case Left(error) =>
            logger.debug(s"Could not apply cache group ${cacheGroup} because of $error")
            state
          case Right(resolved) =>
            logger.debug(s"Could apply cache group ${cacheGroup}")
            state.copy(
              readFiles = state.readFiles ++ resolved,
              cacheHits = state.cacheHits + cacheGroup
            )
        }
      }
  }

  /** Open a Cache File. Returns lefty values if something fails. */
  private def openCacheFile(cacheKey: CacheKey, cacheFiles: Map[CacheKey, PlanFileReference]): Future[Either[String, (PlanFileReference, FileRepository.FileGetResult)]] = {
    // There must be a file ref for it.
    val fileRef = cacheFiles.get(cacheKey) match {
      case None          => return Future.successful(Left("Cache key has no file associated"))
      case Some(fileRef) => fileRef
    }

    val fileId = fileCache.get(cacheKey) match {
      case None     => return Future.successful(Left("Cache miss"))
      case Some(id) => id
    }

    val fileGet = fileRepository.requestFileGet(fileId)
    fileGet.map { fileGetResult =>
      Right(fileRef -> fileGetResult)
    }.recover {
      case e => Left(e.getMessage)
    }
  }

  private def unflattenEither[L, R](in: List[Either[L, R]]): Either[L, List[R]] = {
    import cats.implicits._
    in.sequence
  }

  private def openFile(state: ExecutionOpenFiles, file: PlanFile): Future[ExecutionOpenFiles] = {
    if (file.cacheKey.isDefined) {
      if (state.readFiles.contains(file.ref)) {
        // already opened from caching
        return Future.successful(state)
      }
    }
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
        state.writeFiles + (file.ref -> writeResult)
      }.getOrElse(state.writeFiles)

      val newReadFiles = readResult.map { readResult =>
        state.readFiles + (file.ref -> readResult)
      }.getOrElse(state.readFiles)

      state.copy(
        writeFiles = newWriteFiles,
        readFiles = newReadFiles
      )
    }
  }
}

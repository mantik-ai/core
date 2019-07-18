package ai.mantik.planner.repository.impl

import java.io.{ File, FileNotFoundException }
import java.nio.charset.StandardCharsets
import java.nio.file.{ CopyOption, Files, Path, StandardCopyOption }
import java.time.temporal.{ ChronoUnit, Temporal, TemporalUnit }
import java.time.{ Clock, Instant }
import java.util.UUID

import ai.mantik.componently.AkkaRuntime
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.planner.repository.{ Errors, FileRepository }
import ai.mantik.planner.repository.Errors.RepositoryError
import ai.mantik.planner.repository.FileRepository.{ FileGetResult, FileStorageResult }
import ai.mantik.planner.repository.impl.LocalFileRepository.FileMetaData
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import akka.util.ByteString
import com.typesafe.config.Config
import io.circe.{ Decoder, Encoder }

import scala.concurrent.{ ExecutionContext, Future }
import io.circe.syntax._
import io.circe.parser
import org.apache.commons.io.FileUtils

import scala.concurrent.duration._
import scala.collection.JavaConverters._

/**
 * A FileRepository which is using a local file system for storing files.
 *
 * Note:
 * File operations are not always synchronized. It tries to mimic a little bit of collision safety
 * by writing new files to ".part" files and then rename it (which is atomic on most modern file systems).
 * However it's possible to construct a clash in meta data overriding. In practice the risk should be small
 * when used with a local user .
 *
 * Temporary files are cleaned up periodically. This should scale to some 1000 files but not more.
 */
class LocalFileRepository(val directory: Path)(implicit akkaRuntime: AkkaRuntime) extends FileRepositoryServer {

  def this()(implicit akkaRuntime: AkkaRuntime) {
    this(new File(akkaRuntime.config.getString("mantik.repository.fileRepository.local.directory")).toPath)
  }

  logger.info(s"Initializing Local File Repository in directory ${directory}")

  val cleanupInterval = Duration.fromNanos(subConfig.getDuration("local.cleanupInterval").toNanos)
  val cleanupTimeout = Duration.fromNanos(subConfig.getDuration("local.cleanupTimeout").toNanos)

  ensureDirectory()

  private def ensureDirectory(): Unit = {
    try {
      Files.createDirectories(directory)
    } catch {
      case e: Exception =>
        throw new RepositoryError("Could not create directory for local file repository. Check configuration.", e)
    }
  }

  private[impl] val timeoutScheduler = actorSystem.scheduler.schedule(
    10.seconds,
    cleanupInterval
  ) {
      removeTimeoutedFiles()
    }

  override def shutdown(): Unit = {
    timeoutScheduler.cancel()
    super.shutdown()
  }

  private def makeNewId(): String = UUID.randomUUID().toString

  override def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult] = {
    Future {
      val meta = FileMetaData(
        temporary = temporary,
        requestTime = akkaRuntime.clock.instant()
      )
      val id = makeNewId()

      saveMeta(id, meta)

      logger.debug(s"Requested storage of new file ${id}")

      FileStorageResult(
        fileId = id,
        path = makePath(id)
      )
    }
  }

  override def requestFileGet(id: String, optimistic: Boolean): Future[FileRepository.FileGetResult] = {
    Future {
      val fileMeta = loadMeta(id)
      val fileExits = fileName(id).toFile.isFile()
      if (!optimistic && !fileExits) {
        logger.info(s"File ${id} is not existing and request is not optimistic")
        throw new Errors.NotFoundException(s"File ${id} is not yet written")
      }
      FileGetResult(
        id, makePath(id), fileMeta.contentType
      )
    }
  }

  override def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Unit]]] = {
    for {
      meta <- Future(loadMeta(id))
      part = partFileName(id)
      file = fileName(id)
      sink = FileIO.toPath(part)
    } yield {
      sink.mapMaterializedValue { writeResult =>
        writeResult.map { ioResult =>
          logger.info(s"Written ${ioResult.count} bytes to ${part}, moving to ${file}")
          Files.move(part, file, StandardCopyOption.ATOMIC_MOVE)
          val newMeta = meta.copy(contentType = Some(contentType))
          saveMeta(id, newMeta)
          (())
        }
      }
    }
  }

  override def loadFile(id: String): Future[Source[ByteString, _]] = {
    Future {
      val name = fileName(id)
      val exists = Files.isRegularFile(name)
      if (!exists) {
        throw new Errors.NotFoundException(s"File ${id} doesn't exist")
      }
      FileIO.fromPath(name)
    }
  }

  private def fileName(id: String): Path = {
    resolve(id, "")
  }

  private def metaFileName(id: String): Path = {
    resolve(id, LocalFileRepository.MetaEnding)
  }

  private def partFileName(id: String): Path = {
    resolve(id, ".part")
  }

  private def resolve(id: String, ending: String): Path = {
    require(!id.contains("/") && !id.contains("."), "Ids may not contain / or .")
    require(ending.isEmpty || ending.startsWith("."))
    require(!ending.contains("/"))
    require(!ending.contains("\\"))
    directory.resolve(id + ending)
  }

  private def loadMeta(id: String): FileMetaData = {
    val metaJson = try {
      FileUtils.readFileToString(metaFileName(id).toFile, StandardCharsets.UTF_8)
    } catch {
      case e: FileNotFoundException =>
        logger.debug(s"File ${id} not found when loading meta data")
        throw new Errors.NotFoundException(s"File with id ${id} not found")
    }
    val parsed = parser.parse(metaJson).flatMap(_.as[FileMetaData]) match {
      case Left(error) => throw new RuntimeException("Could not parse Metadata", error)
      case Right(ok)   => ok
    }
    parsed
  }

  private def saveMeta(id: String, meta: FileMetaData): Unit = {
    val metaFile = metaFileName(id)
    FileUtils.write(metaFile.toFile, meta.asJson.spaces2, StandardCharsets.UTF_8)
  }

  def removeTimeoutedFiles(): Unit = {
    val border = akkaRuntime.clock.instant().minus(cleanupTimeout.toSeconds, ChronoUnit.SECONDS)
    logger.debug(s"Checking for timeouted files, border: ${border}")
    findTimeoutedFiles(border).foreach { timeoutedFileId =>
      deleteFileImpl(timeoutedFileId)
    }
  }

  private def findTimeoutedFiles(border: Instant): Iterator[String] = {
    listFiles().filter { id =>
      try {
        val meta = loadMeta(id)
        meta.temporary && meta.requestTime.isBefore(border)
      } catch {
        case e: Exception =>
          // can happen due various race conditions
          logger.debug("Could not analyze meta", e)
          false
      }
    }
  }

  /** List file IDs. */
  private[impl] def listFiles(): Iterator[String] = {
    Files.list(directory).iterator().asScala.collect {
      case file if file.getFileName.toString.endsWith(LocalFileRepository.MetaEnding) =>
        val id = file.getFileName.toString.stripSuffix(LocalFileRepository.MetaEnding)
        id
    }
  }

  override def deleteFile(id: String): Future[Boolean] = {
    Future {
      deleteFileImpl(id)
    }
  }

  /** Delete a file, does nothing if the file doesn't exist. */
  private def deleteFileImpl(id: String): Boolean = {
    logger.debug(s"Deleting ${id}...")
    try {
      val exists = Files.deleteIfExists(fileName(id))
      Files.deleteIfExists(partFileName(id))
      Files.deleteIfExists(metaFileName(id))
      exists
    } catch {
      case e: Exception =>
        logger.warn("Error on deleting file", e)
        false
    }
  }
}

object LocalFileRepository {

  /** File name ending for Meta Data. */
  val MetaEnding = ".meta"

  /** Meta data which is stored for each file. */
  case class FileMetaData(
      contentType: Option[String] = None,
      temporary: Boolean = false,
      requestTime: Instant
  )

  import io.circe.java8.time._
  implicit val metaDataFormat: Encoder[FileMetaData] with Decoder[FileMetaData] = CirceJson.makeSimpleCodec[FileMetaData]

  def createTemporary()(implicit akkaRuntime: AkkaRuntime): LocalFileRepository = {
    new LocalFileRepository(Files.createTempDirectory("mantik_simple_storage")) {
      override def shutdown(): Unit = {
        super.shutdown()
        FileUtils.deleteDirectory(directory.toFile)
      }
    }
  }
}

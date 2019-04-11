package ai.mantik.repository.impl

import java.nio.file.{ Files, Path }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpEntity, MediaType, MediaTypes }
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import akka.util.ByteString
import ai.mantik.repository.FileRepository
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.Success
import ai.mantik.repository.Errors
import com.typesafe.config.Config

/** Simple local file service, for development, no security at all. */
class SimpleTempFileRepository(config: Config)(implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext) extends FileRepository {
  private val logger = LoggerFactory.getLogger(getClass)

  val directory = Files.createTempDirectory("mantik_simple_storage")

  // Note: the file service must be enabled in kubernetes.
  // inside the Mantik namespace

  private val subConfig = config.getConfig("mantik.repository.fileRepository")
  val port = subConfig.getInt("port")
  val interface = subConfig.getString("interface")
  val externalServiceName = subConfig.getString("externalServiceName")
  val externalUrl = s"http://${externalServiceName}/files/"

  case class FileInfo(temporary: Boolean, written: Option[Long] = None, contentType: Option[String] = None)

  object lock
  val files = mutable.Map.empty[String, FileInfo]
  var nextId = 1

  val HelloMessage = "This is a Mantik SimpleTempFileRepository, do not use in production."

  val route = concat(
    path("/") {
      get(
        complete(HelloMessage)
      )
    },
    path("files") {
      get {
        complete(HelloMessage)
      }
    },
    path("files" / "") {
      get {
        complete(HelloMessage)
      }
    },
    path("files" / Remaining) { id =>
      post {
        fileStatus(id) match {
          case None =>
            logger.warn(s"Non existing file ${id} for writing requested")
            complete(404, "File not found")
          case Some(file) =>
            extractRequest { req =>
              val fileName = localFileName(id)
              logger.info(s"Streaming write of ${id} to ${fileName}")
              val sink = FileIO.toPath(fileName)
              val writeResult: Future[String] = req.entity.dataBytes.runWith(sink)
                .andThen {
                  case Success(io) => setFileStatus(id, _.copy(written = Some(io.count), contentType = Some(req.entity.contentType.value)))
                }
                .map(_ => "")

              complete(200, writeResult)
            }
        }
      } ~
        get {
          fileStatus(id) match {
            case None =>
              logger.warn(s"Non existing file ${id} for getting requested")
              complete(404, "File not found")
            case Some(f) if f.written.isEmpty =>
              logger.warn(s"Existing file ${id} without content requested")
              complete(404, "File has no content yet")
            case Some(f) =>
              val path = localFileName(id)
              val source = FileIO.fromPath(path)
              val mediaType = f.contentType.map(findAkkaMediaType).getOrElse(
                MediaTypes.`application/octet-stream`
              )
              extractRequest { request =>
                complete(
                  HttpEntity(mediaType, source)
                )
              }
          }
        }
    }, path("") {
      get {
        complete("Mantik SimpleTempFileRepository")
      }
    }
  )

  private def findAkkaMediaType(name: String): MediaType.Binary = {
    name.split("/").toList match {
      case List(a, b) =>
        MediaTypes.getForKey(a -> b) match {
          case Some(b: MediaType.Binary) => b
          case _ =>
            MediaType.customBinary(a, b, MediaType.Compressible)
        }
      case somethingElse =>
        logger.error(s"Illegal Content Type ${name}")
        MediaTypes.`application/octet-stream`
    }
  }

  private def fileStatus(id: String): Option[FileInfo] = {
    lock.synchronized {
      return files.get(id)
    }
  }

  private def setFileStatus(id: String, f: FileInfo => FileInfo): Unit = {
    lock.synchronized {
      files.get(id).map(f).foreach { updated =>
        files.put(id, updated)
      }

    }
  }

  val bindResult = Await.result(Http().bindAndHandle(route, interface, port), 60.seconds)
  logger.info(s"Listening on ${port}, external ${externalUrl}, do not forget to add it to the cluster")

  override def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult] = {
    lock.synchronized {
      val id = nextId.toString
      nextId += 1
      files(id) = FileInfo(temporary = temporary)
      Future.successful(
        FileRepository.FileStorageResult(
          id, externalUrl, resource = id
        )
      )
    }
  }

  override def requestFileGet(id: String, optimistic: Boolean): Future[FileRepository.FileGetResult] = {
    fileStatus(id) match {
      case None => Future.failed(new Errors.NotFoundException(s"File ${id} not found"))
      case Some(file) if !optimistic && file.written.isEmpty => Future.failed(new Errors.NotFoundException(s"File ${id} ha no content yet"))
      case Some(file) =>
        Future.successful(
          FileRepository.FileGetResult(
            id, externalUrl, resource = id, contentType = file.contentType
          )
        )
    }
  }

  override def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Unit]]] = {
    fileStatus(id) match {
      case None => return Future.failed(new Errors.NotFoundException(s"File ${id} not found"))
      case Some(file) =>
        val resolved = localFileName(id)
        val sink = FileIO.toPath(resolved).mapMaterializedValue { ioResult =>
          ioResult.andThen {
            case Success(s) => setFileStatus(id, _.copy(written = Some(s.count), contentType = Some(contentType)))
          }.map(_ => ())
        }
        Future.successful(
          sink
        )
    }
  }

  override def loadFile(id: String): Future[Source[ByteString, _]] = {
    fileStatus(id) match {
      case None => Future.failed(new Errors.NotFoundException(s"File ${id} not found"))
      case Some(file) if file.written.isEmpty =>
        Future.failed(new Errors.NotFoundException(s"File ${id} ha no content yet"))
      case Some(file) =>
        val resolved = localFileName(id)
        Future.successful(FileIO.fromPath(resolved))
    }
  }

  private def makeExternalName(id: String): String = {
    externalUrl + "/" + id
  }

  private def localFileName(id: String): Path = {
    val resolved = directory.resolve(id)
    if (!resolved.startsWith(directory)) {
      // security breach
      throw new Errors.NotFoundException(s"ID may not lead out of storage directory")
    }
    resolved
  }

  def shutdown(): Unit = {
    bindResult.terminate(60.seconds)
  }

  def boundPort: Int = {
    bindResult.localAddress.getPort
  }
}

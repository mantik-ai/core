package ai.mantik.repository.impl

import java.net.{ Inet4Address, InetAddress, InetSocketAddress, NetworkInterface }
import java.nio.file.{ Files, Path }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpEntity, MediaType, MediaTypes, Uri }
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
import akka.io.Inet
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
                  case Success(io) =>
                    logger.info(s"Wrote ${fileName} with ${io.count} bytes")
                    setFileStatus(id, _.copy(written = Some(io.count), contentType = Some(req.entity.contentType.value)))
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
  logger.info(s"Listening on ${interface}:${boundPort}, external ${address}")

  override def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult] = {
    lock.synchronized {
      val id = nextId.toString
      nextId += 1
      files(id) = FileInfo(temporary = temporary)
      Future.successful(
        FileRepository.FileStorageResult(
          id, makePath(id), resource = id
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
            id, makePath(id), resource = id, contentType = file.contentType
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

  private def makePath(id: String): String = {
    "files/" + id
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

  override def address(): InetSocketAddress = _address

  private lazy val _address = figureOutAddress()

  private def figureOutAddress(): InetSocketAddress = {
    // This is tricky: https://stackoverflow.com/questions/9481865/getting-the-ip-address-of-the-current-machine-using-java
    // We can't know which one is available from kubernetes
    // hopefully the first non-loopback is it.
    import scala.collection.JavaConverters._
    val addresses = (for {
      networkInterface <- NetworkInterface.getNetworkInterfaces.asScala
      if !networkInterface.isLoopback
      address <- networkInterface.getInetAddresses.asScala
    } yield address).toVector
    // We prefer ipv4
    val address = addresses.collectFirst {
      case ipv4: Inet4Address => ipv4
    }.orElse(addresses.headOption).getOrElse(InetAddress.getLocalHost)
    logger.info(s"Choosing ${address} from ${addresses}")
    new InetSocketAddress(address, boundPort)
  }
}

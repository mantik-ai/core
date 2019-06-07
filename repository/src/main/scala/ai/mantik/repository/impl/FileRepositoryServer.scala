package ai.mantik.repository.impl

import java.net.{ Inet4Address, InetAddress, InetSocketAddress, NetworkInterface }

import ai.mantik.repository.{ Errors, FileRepository }
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpEntity, MediaType, MediaTypes }
import akka.http.scaladsl.server.Directives.{ complete, concat, extractRequest, get, path, post }
import com.typesafe.config.Config
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/** Implements the HTTP server parts from the [[FileRepository]] trait. */
abstract class FileRepositoryServer(config: Config)(implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext) extends FileRepository {
  protected val logger = LoggerFactory.getLogger(getClass)

  val HelloMessage = "This is a Mantik File Repository"

  protected val subConfig = config.getConfig("mantik.repository.fileRepository")
  protected val port = subConfig.getInt("port")
  protected val interface = subConfig.getString("interface")

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
        extractRequest { req =>
          val contentType = req.entity.contentType.value
          val result = storeFile(id, contentType).flatMap { sink =>
            req.entity.dataBytes.runWith(sink)
          }
          onComplete(result) {
            case Success(_)                           => complete(200, "")
            case Failure(e: Errors.NotFoundException) => complete(404, "File not found")
            case Failure(other) =>
              logger.error("Error on adding file", other)
              complete(500, "Internal server error")
          }
        }
      } ~
        get {
          onComplete(for {
            fileGet <- requestFileGet(id)
            fileSource <- loadFile(id)
          } yield {
            fileGet.contentType -> fileSource
          }) {
            case Success((contentType, fileSource)) =>
              val mediaType = contentType.map(findAkkaMediaType).getOrElse(
                MediaTypes.`application/octet-stream`
              )
              complete(
                HttpEntity(mediaType, fileSource)
              )
            case Failure(e: Errors.NotFoundException) =>
              logger.warn(s"File ${id} not found")
              complete(404, "File not found")
            case Failure(other) =>
              logger.error("Error on requesting file", other)
              complete(500, "Internal servre error")
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

  val bindResult = Await.result(Http().bindAndHandle(route, interface, port), 60.seconds)
  logger.info(s"Listening on ${interface}:${boundPort}, external ${address}")

  override def shutdown(): Unit = {
    bindResult.terminate(60.seconds)
  }

  def boundPort: Int = {
    bindResult.localAddress.getPort
  }

  /** Returns the relative path under which a file is available via HTTP. */
  protected def makePath(id: String): String = {
    "files/" + id
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

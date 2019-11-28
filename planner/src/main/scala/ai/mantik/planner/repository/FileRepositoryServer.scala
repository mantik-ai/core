package ai.mantik.planner.repository

import java.net.{ Inet4Address, InetAddress, NetworkInterface }

import ai.mantik.componently.utils.HostPort
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.errors.MantikException
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpEntity, MediaType, MediaTypes }
import akka.http.scaladsl.server.Directives._
import javax.inject.{ Inject, Singleton }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/** HTTP Server for FileRepository, to make it accessible from Executor. */
@Singleton
private[mantik] class FileRepositoryServer @Inject() (fileRepository: FileRepository)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {
  val HelloMessage = "This is a Mantik File Repository"

  private val subConfig = config.getConfig("mantik.fileRepositoryServer")
  private val port = subConfig.getInt("port")
  private val interface = subConfig.getString("interface")

  private val route = concat(
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
          val result = fileRepository.storeFile(id, contentType).flatMap { sink =>
            req.entity.dataBytes.runWith(sink)
          }
          onComplete(result) {
            case Success(_) => complete(200, "")
            case Failure(e: MantikException) if e.code == FileRepository.NotFoundCode => complete(404, "File not found")
            case Failure(other) =>
              logger.error("Error on adding file", other)
              complete(500, "Internal server error")
          }
        }
      } ~
        get {
          onComplete(fileRepository.loadFile(id)) {
            case Success((contentType, fileSource)) =>
              val mediaType = findAkkaMediaType(contentType)
              complete(
                HttpEntity(mediaType, fileSource)
              )
            case Failure(e: MantikException) if e.code == FileRepository.NotFoundCode =>
              logger.warn(s"File ${id} not found (http requested)")
              complete(404, "File not found")
            case Failure(other) =>
              logger.error("Error on requesting file", other)
              complete(500, "Internal servre error")
          }
        }
    }, path("") {
      get {
        complete("Mantik File Repository")
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

  addShutdownHook {
    bindResult.terminate(60.seconds)
  }

  def boundPort: Int = {
    bindResult.localAddress.getPort
  }

  /** Returns the address of the repository (must be reachable from the executor). */
  def address(): HostPort = _address

  private lazy val _address = figureOutAddress()

  private def figureOutAddress(): HostPort = {
    // This is tricky: https://stackoverflow.com/questions/9481865/getting-the-ip-address-of-the-current-machine-using-java
    // We can't know which one is available from kubernetes
    // hopefully the first non-loopback is it.
    import scala.collection.JavaConverters._

    def score(address: InetAddress): Int = {
      address match {
        case v4: Inet4Address =>
          if (v4.getHostAddress.startsWith("192")) {
            +100
          } else {
            50
          }
        case x if x.isLoopbackAddress => -100
        case other                    => 0
      }
    }

    val addresses = (for {
      networkInterface <- NetworkInterface.getNetworkInterfaces.asScala
      if !networkInterface.isLoopback
      address <- networkInterface.getInetAddresses.asScala
    } yield address).toVector

    val ordered = addresses.sortBy(x => 0 - score(x))
    val address = ordered.headOption.getOrElse(InetAddress.getLocalHost)

    val result = HostPort(address.getHostAddress, boundPort)
    logger.info(s"Choosing ${result} from ${ordered}")
    result
  }
}

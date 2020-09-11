package ai.mantik.executor.docker.api

import java.nio.file.Files

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.docker.api.structures._
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.{ Http, HttpsConnectionContext }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import net.reactivecore.fhttp.akka.ApiClient

import scala.concurrent.Future

class DockerClient()(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {

  val intermediateTempFile = Files.createTempFile("docker_client", "p8")

  private val connectSettings = DockerConnectSettings
    .fromConfig(akkaRuntime.config)
    .derive(intermediateTempFile)

  private val httpExt = Http()
  private val clientHttpsSettings: HttpsConnectionContext = connectSettings.sslConfigSettings.map { sslConfigSettings =>
    val akkaSettings = AkkaSSLConfig().withSettings(sslConfigSettings)
    httpExt.createClientHttpsContext(akkaSettings)
  }.getOrElse {
    httpExt.defaultClientHttpsContext
  }

  private def executeRequest(request: HttpRequest): Future[HttpResponse] = {
    val t0 = System.currentTimeMillis()
    httpExt.singleRequest(request, clientHttpsSettings).map { response =>
      val t1 = System.currentTimeMillis()
      logger.debug(s"Called ${request.method.name()} ${request.uri}: ${response.status.intValue()} ${t1 - t0}ms (${response.entity.contentType.value})")
      response
    }
  }

  /** (Guess) the Address of the docker host. */
  lazy val dockerHost: String = {
    connectSettings.asUri.authority.host.address
  }

  logger.info(s"Initializing DockerClient with uri ${connectSettings.asUri}")

  private val apiClient = new ApiClient(req => executeRequest(req), connectSettings.asUri)

  val version: Unit => Future[VersionResponse] =
    wrapError("version", apiClient.prepare(DockerApi.version))

  // Container Management

  val createContainer: (String, CreateContainerRequest) => Future[CreateContainerResponse] =
    wrapError2("createContainer", apiClient.prepare(DockerApi.createContainer))

  val inspectContainer: String => Future[InspectContainerResponse] =
    wrapError("inspectContainer", apiClient.prepare(DockerApi.inspectContainer))

  // Parameter: "all" (otherwise only running containers)
  val listContainers: Boolean => Future[Vector[ListContainerResponseRow]] =
    wrapError("listContainers", apiClient.prepare(DockerApi.listContainers))

  val listContainersFiltered: (Boolean, ListContainerRequestFilter) => Future[Vector[ListContainerResponseRow]] =
    wrapError2("listContainersFiltered", apiClient.prepare(DockerApi.listContainersFiltered))

  val killContainer: String => Future[Unit] =
    wrapError("killContainer", apiClient.prepare(DockerApi.killContainer))

  val removeContainer: (String, Boolean) => Future[Unit] =
    wrapError2("removeContainer", apiClient.prepare(DockerApi.removeContainer))

  val startContainer: String => Future[Unit] =
    wrapError("startContainer", apiClient.prepare(DockerApi.startContainer))

  // Parameters: containerId, stdout, stderr
  val containerLogs: (String, Boolean, Boolean) => Future[String] = {
    case (containerId, stdout, stderr) =>
      // Workaround, Docker sends application/octet-stream
      for {
        logSource <- errorHandler("prepareContainerLogs", preparedContainerLogs(containerId, stdout, stderr))
        collected <- logSource._2.runWith(Sink.seq)
        combined = collected.foldLeft(ByteString.empty)(_ ++ _)
      } yield combined.utf8String
  }

  val containerWait: String => Future[ContainerWaitResponse] = {
    wrapError("containerWait", apiClient.prepare(DockerApi.containerWait))
  }

  private val preparedContainerLogs = apiClient.prepare(DockerApi.containerLogs)

  // Images

  val pullImage: String => Future[(String, Source[ByteString, _])] =
    wrapError("pullImage", apiClient.prepare(DockerApi.pullImage))

  val inspectImage: String => Future[InspectImageResult] =
    wrapError("inspectImage", apiClient.prepare(DockerApi.inspectImage))

  val removeImage: String => Future[Vector[RemoveImageRow]] =
    wrapError("removeImage", apiClient.prepare(DockerApi.removeImage))

  // Volumes
  val listVolumes: Unit => Future[ListVolumeResponse] =
    wrapError("listVolumes", apiClient.prepare(DockerApi.listVolumes))

  val inspectVolume: String => Future[InspectVolumeResponse] =
    wrapError("inspectVolume", apiClient.prepare(DockerApi.inspectVolume))

  val createVolume: CreateVolumeRequest => Future[CreateVolumeResponse] =
    wrapError("createVolume", apiClient.prepare(DockerApi.createVolume))

  val removeVolume: String => Future[Unit] =
    wrapError("removeVolume", apiClient.prepare(DockerApi.removeVolume))

  // Networks
  val listNetworks: Unit => Future[Vector[ListNetworkResponseRow]] =
    wrapError("listNetworks", apiClient.prepare(DockerApi.listNetworks))

  val listNetworksFiltered: ListNetworkRequestFilter => Future[Vector[ListNetworkResponseRow]] =
    wrapError("listNetworksFiltered", apiClient.prepare(DockerApi.listNetworksFiltered))

  val inspectNetwork: String => Future[InspectNetworkResult] =
    wrapError("inspectNetwork", apiClient.prepare(DockerApi.inspectNetwork))

  val createNetwork: CreateNetworkRequest => Future[CreateNetworkResponse] =
    wrapError("createNetwork", apiClient.prepare(DockerApi.createNetwork))

  val removeNetwork: String => Future[Unit] =
    wrapError("removeNetwork", apiClient.prepare(DockerApi.removeNetwork))

  private def wrapError[A, R](op: String, f: A => Future[Either[(Int, ErrorResponse), R]]): A => Future[R] = {
    args => errorHandler(op, f(args))
  }

  private def wrapError2[A1, A2, R](op: String, f: ((A1, A2)) => Future[Either[(Int, ErrorResponse), R]]): (A1, A2) => Future[R] = {
    (a1, a2) => errorHandler(op, f(a1, a2))
  }

  private def wrapError3[A1, A2, A3, R](op: String, f: ((A1, A2, A3)) => Future[Either[(Int, ErrorResponse), R]]): (A1, A2, A3) => Future[R] = {
    (a1, a2, a3) => errorHandler(op, f(a1, a2, a3))
  }

  private def errorHandler[R](op: String, in: Future[Either[(Int, ErrorResponse), R]]): Future[R] = {
    in.flatMap {
      case Left((code, value)) => Future.failed(DockerClient.WrappedErrorResponse(code, value, op))
      case Right(ok)           => Future.successful(ok)
    }
  }

  addShutdownHook {
    Files.delete(intermediateTempFile)
    Future.successful(())
  }
}

object DockerClient {
  /** A docker error as wrapped message in a RuntimeException. */
  case class WrappedErrorResponse(code: Int, error: ErrorResponse, operation: String) extends RuntimeException(
    s"Docker error: ${code} ${error.message} (operation=${operation})"
  )
}
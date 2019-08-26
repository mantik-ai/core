package ai.mantik.executor.docker.api

import java.nio.file.Files

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.docker.api.structures.{ ContainerWaitResponse, CreateContainerRequest, CreateContainerResponse, CreateVolumeRequest, CreateVolumeResponse, InspectContainerResponse, InspectImageResult, InspectVolumeResponse, ListContainerRequestFilter, ListContainerResponseRow, ListVolumeResponse, RemoveImageRow, VersionResponse }
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
    wrapError(apiClient.prepare(DockerApi.version))

  val createContainer: (String, CreateContainerRequest) => Future[CreateContainerResponse] =
    wrapError2(apiClient.prepare(DockerApi.createContainer))

  val inspectContainer: String => Future[InspectContainerResponse] =
    wrapError(apiClient.prepare(DockerApi.inspectContainer))

  // Parameter: "all"
  val listContainers: Boolean => Future[List[ListContainerResponseRow]] =
    wrapError(apiClient.prepare(DockerApi.listContainers))

  val listContainersFiltered: (Boolean, ListContainerRequestFilter) => Future[Vector[ListContainerResponseRow]] =
    wrapError2(apiClient.prepare(DockerApi.listContainersFiltered))

  val killContainer: String => Future[Unit] =
    wrapError(apiClient.prepare(DockerApi.killContainer))

  val removeContainer: (String, Boolean) => Future[Unit] =
    wrapError2(apiClient.prepare(DockerApi.removeContainer))

  val startContainer: String => Future[Unit] =
    wrapError(apiClient.prepare(DockerApi.startContainer))

  // Parameters: containerId, stdout, stderr
  val containerLogs: (String, Boolean, Boolean) => Future[String] = {
    case (containerId, stdout, stderr) =>
      // Workaround, Docker sends application/octet-stream
      for {
        logSource <- errorHandler(preparedContainerLogs(containerId, stdout, stderr))
        collected <- logSource._2.runWith(Sink.seq)
        combined = collected.foldLeft(ByteString.empty)(_ ++ _)
      } yield combined.utf8String
  }

  val containerWait: String => Future[ContainerWaitResponse] = {
    wrapError(apiClient.prepare(DockerApi.containerWait))
  }

  private val preparedContainerLogs = apiClient.prepare(DockerApi.containerLogs)

  val pullImage: String => Future[(String, Source[ByteString, _])] =
    wrapError(apiClient.prepare(DockerApi.pullImage))

  val inspectImage: String => Future[InspectImageResult] =
    wrapError(apiClient.prepare(DockerApi.inspectImage))

  val removeImage: String => Future[Vector[RemoveImageRow]] =
    wrapError(apiClient.prepare(DockerApi.removeImage))

  val listVolumes: Unit => Future[ListVolumeResponse] =
    wrapError(apiClient.prepare(DockerApi.listVolumes))

  val inspectVolume: String => Future[InspectVolumeResponse] =
    wrapError(apiClient.prepare(DockerApi.inspectVolume))

  val createVolume: CreateVolumeRequest => Future[CreateVolumeResponse] =
    wrapError(apiClient.prepare(DockerApi.createVolume))

  val removeVolume: String => Future[Unit] =
    wrapError(apiClient.prepare(DockerApi.removeVolume))

  private def wrapError[A, R](f: A => Future[Either[(Int, ErrorResponse), R]]): A => Future[R] = {
    args => errorHandler(f(args))
  }

  private def wrapError2[A1, A2, R](f: ((A1, A2)) => Future[Either[(Int, ErrorResponse), R]]): (A1, A2) => Future[R] = {
    (a1, a2) => errorHandler(f(a1, a2))
  }

  private def wrapError3[A1, A2, A3, R](f: ((A1, A2, A3)) => Future[Either[(Int, ErrorResponse), R]]): (A1, A2, A3) => Future[R] = {
    (a1, a2, a3) => errorHandler(f(a1, a2, a3))
  }

  private def errorHandler[R](in: Future[Either[(Int, ErrorResponse), R]]): Future[R] = {
    in.flatMap {
      case Left((code, value)) => Future.failed(DockerClient.WrappedErrorResponse(code, value))
      case Right(ok)           => Future.successful(ok)
    }
  }

  override def shutdown(): Unit = {
    super.shutdown()
    Files.delete(intermediateTempFile)
  }
}

object DockerClient {
  /** A docker error as wrapped message in a RuntimeException. */
  case class WrappedErrorResponse(code: Int, error: ErrorResponse) extends RuntimeException(
    s"Docker error: ${code} ${error.message}"
  )
}
package ai.mantik.elements.registry.api

import ai.mantik.elements.{ MantikId, NamedMantikId }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import net.reactivecore.fhttp.akka.ApiClient

import scala.concurrent.{ ExecutionContext, Future }

/** Implements raw API Calls to Mantik Registry. */
class MantikRegistryApi(rootUri: Uri, executor: ApiClient.RequestExecutor)(implicit ec: ExecutionContext, mat: Materializer) {
  val apiPath = Uri("api").resolvedAgainst(rootUri)
  val apiClient = new ApiClient(executor, apiPath)

  private def orFail[A, T](in: A => Future[Either[(Int, ApiErrorResponse), T]]): A => Future[T] = {
    arg =>
      in(arg).flatMap {
        case Left((code, failure)) => Future.failed(MantikRegistryApi.WrappedError(failure))
        case Right(in)             => Future.successful(in)
      }
  }

  /** Execute a login call. */
  val login: ApiLoginRequest => Future[ApiLoginResponse] = orFail(apiClient.prepare(MantikRegistryApiCalls.login))

  /** Execute a Login Status call. */
  val loginStatus: String => Future[ApiLoginStatusResponse] = orFail(apiClient.prepare(MantikRegistryApiCalls.loginStatus))

  /** Retrieve an artifact. */
  val artifact: ((String, MantikId)) => Future[ApiGetArtifactResponse] = orFail(apiClient.prepare(MantikRegistryApiCalls.artifact))

  /** Tag an Artifact. */
  val tag: ((String, ApiTagRequest)) => Future[ApiTagResponse] = orFail(apiClient.prepare(MantikRegistryApiCalls.tag))

  /**
   * Retrieve an artifact file.
   * (With token and fileId)
   * @return content type and byte source.
   */
  val file: ((String, String)) => Future[(String, Source[ByteString, _])] = orFail(apiClient.prepare(MantikRegistryApiCalls.file))

  /** Prepares the upload of a file. */
  val prepareUpload: ((String, ApiPrepareUploadRequest)) => Future[ApiPrepareUploadResponse] = orFail(apiClient.prepare(MantikRegistryApiCalls.prepareUpload))

  /** Uploads the payload of an artifact. */
  val uploadFile: ((String, String, String, Source[ByteString, _])) => Future[ApiFileUploadResponse] = orFail(apiClient.prepare(MantikRegistryApiCalls.uploadFile))
}

object MantikRegistryApi {

  // A Wrapped error from the API
  case class WrappedError(apiErrorResponse: ApiErrorResponse) extends RuntimeException(apiErrorResponse.code + " " + apiErrorResponse.message.getOrElse(""))
}

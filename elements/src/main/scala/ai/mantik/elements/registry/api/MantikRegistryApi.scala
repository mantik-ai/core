package ai.mantik.elements.registry.api

import ai.mantik.elements.MantikId
import ai.mantik.util.http.SimpleHttpClient
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }

/** Implements raw API Calls to Mantik Registry. */
class MantikRegistryApi(rootUri: Uri, http: HttpExt)(implicit ec: ExecutionContext, mat: Materializer) {
  private val logger = LoggerFactory.getLogger(getClass)
  val apiPath = Uri("api/").resolvedAgainst(rootUri)
  val client = new SimpleHttpClient[ApiErrorResponse](apiPath, http, logger)
  val TokenHeaderName = "AUTH_TOKEN"

  /** Execute a login call. */
  def login(loginRequest: ApiLoginRequest): Future[ApiLoginResponse] = {
    client
      .post("login")
      .withJsonPayload(loginRequest)
      .withJsonResponse[ApiLoginResponse]
      .execute()
  }

  /** Execute a Login Status call. */
  def loginStatus(token: String): Future[ApiLoginStatusResponse] = {
    client
      .get("login_status")
      .addHeader(TokenHeaderName, token)
      .withJsonResponse[ApiLoginStatusResponse]
      .execute()
  }

  /** Retrieve an artifact. */
  def artifact(token: String, mantikId: MantikId): Future[ApiGetArtifactResponse] = {
    client
      .get("artifact")
      .addQuery("mantikId" -> mantikId.toString)
      .addHeader(TokenHeaderName, token)
      .withJsonResponse[ApiGetArtifactResponse]
      .execute()
  }

  /** Tag an Artifact. */
  def tag(token: String, request: ApiTagRequest): Future[ApiTagResponse] = {
    client
      .post("artifact/tag")
      .addHeader(TokenHeaderName, token)
      .withJsonPayload(request)
      .withJsonResponse[ApiTagResponse]
      .execute()
  }

  /**
   * Retrieve an artifact file.
   * @return content type and byte source.
   */
  def file(token: String, fileId: String): Future[(String, Source[ByteString, _])] = {
    client
      .get("artifact/file")
      .addQuery("fileId" -> fileId)
      .addHeader(TokenHeaderName, token)
      .executeStream()
  }

  /** Preperaes the upload of a file. */
  def prepareUpload(token: String, request: ApiPrepareUploadRequest): Future[ApiPrepareUploadResponse] = {
    client
      .post("artifact")
      .addHeader(TokenHeaderName, token)
      .withJsonPayload(request)
      .withJsonResponse[ApiPrepareUploadResponse]
      .execute()
  }

  /** Uploades the payload of an artifact. */
  def uploadFile(token: String, itemId: String, contentType: String, data: Source[ByteString, _]): Future[ApiFileUploadResponse] = {
    client
      .post("artifact/file")
      .addHeader(TokenHeaderName, token)
      .withMultipartPayload(
        _.addString("itemId", itemId)
          .addBinaryStream("file", contentType, data, fileName = Some("file"))
      )
      .withJsonResponse[ApiFileUploadResponse]
      .execute()
  }
}

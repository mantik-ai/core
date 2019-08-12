package ai.mantik.planner.repository.impl

import ai.mantik.componently.utils.SecretReader
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.registry.api._
import ai.mantik.elements.{ ItemId, MantikId, Mantikfile }
import ai.mantik.planner.repository.{ Errors, MantikArtifact, MantikRegistry }
import ai.mantik.util.http.SimpleHttpClient
import akka.http.scaladsl.Http
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import javax.inject.Inject

import scala.concurrent.Future
import scala.util.Try

class MantikRegistryImpl @Inject() (implicit akkaRuntime: AkkaRuntime)
  extends ComponentBase with MantikRegistry with FailFastCirceSupport {

  private val configRoot = config.getConfig("mantik.core.registry")
  private val url = configRoot.getString("url")

  private val user = configRoot.getString("username")
  private val password = new SecretReader("password", configRoot)

  private val http = Http()
  private val api = new MantikRegistryApi(url, http)
  private val tokenProvider = new MantikRegistryTokenProvider(api, user, password)

  override def get(mantikId: MantikId): Future[MantikArtifact] = {
    errorHandling {
      for {
        token <- tokenProvider.getToken()
        artifactResponse <- api.artifact(token, mantikId)
        artifact <- Future.fromTry(decodeMantikArtifact(mantikId, artifactResponse))
      } yield artifact
    }
  }

  override def ensureMantikId(itemId: ItemId, mantikId: MantikId): Future[Boolean] = {
    errorHandling {
      for {
        token <- tokenProvider.getToken()
        response <- api.tag(token, ApiTagRequest(itemId.toString, mantikId.toString))
      } yield response.updated
    }
  }

  private def decodeMantikArtifact(mantikId: MantikId, apiGetArtifactResponse: ApiGetArtifactResponse): Try[MantikArtifact] = {
    for {
      mantikfile <- Mantikfile.fromYaml(apiGetArtifactResponse.mantikDefinition).toTry
    } yield {
      MantikArtifact(
        mantikfile,
        fileId = apiGetArtifactResponse.fileId,
        id = mantikId,
        itemId = ItemId(apiGetArtifactResponse.itemId)
      )
    }
  }

  private def errorHandling[T](f: => Future[T]): Future[T] = {
    f.recoverWith {
      case e: Errors.RepositoryError => Future.failed(e) // already mapped
      case e: SimpleHttpClient.ParsedErrorException[ApiErrorResponse @unchecked] =>
        e.message.code match {
          case x if x == ApiErrorResponse.NotFound => Future.failed(new Errors.NotFoundException(e.message.message.getOrElse("")))
          case _                                   => Future.failed(e)
        }
      case e => Future.failed(new Errors.RepositoryError(e.getMessage, e))
    }
  }

  override def getPayload(fileId: String): Future[PayloadSource] = {
    errorHandling {
      for {
        token <- tokenProvider.getToken()
        stream <- api.file(token, fileId)
      } yield stream
    }
  }

  override def addMantikArtifact(mantikArtifact: MantikArtifact, payload: Option[PayloadSource]): Future[MantikArtifact] = {
    errorHandling {
      for {
        token <- tokenProvider.getToken()
        uploadResponse <- api.prepareUpload(
          token,
          ApiPrepareUploadRequest(
            mantikId = Option(mantikArtifact.id).filterNot(_.isAnonymous).map(_.toString),
            itemId = mantikArtifact.itemId.toString,
            mantikfile = mantikArtifact.mantikfile.toJson,
            hasFile = payload.nonEmpty
          )
        )
        remoteFileId <- payload match {
          case Some((contentType, source)) =>
            api.uploadFile(
              token, uploadResponse.itemId, contentType, source
            ).map(response => Some(response.fileId))
          case None =>
            Future.successful(None)
        }
      } yield {
        val result = mantikArtifact.copy(
          fileId = remoteFileId
        )
        result
      }
    }
  }
}

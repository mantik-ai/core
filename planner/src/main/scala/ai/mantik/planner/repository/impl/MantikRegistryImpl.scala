package ai.mantik.planner.repository.impl

import ai.mantik.componently.utils.SecretReader
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.registry.api._
import ai.mantik.elements.{ ItemId, MantikId, Mantikfile, NamedMantikId }
import ai.mantik.planner.repository.MantikRegistry.PayloadSource
import ai.mantik.planner.repository.{ Errors, MantikArtifact, RemoteMantikRegistry }
import akka.http.scaladsl.Http
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import javax.inject.Inject
import net.reactivecore.fhttp.akka.ApiClient

import scala.concurrent.Future
import scala.util.{ Success, Try }

private[mantik] class MantikRegistryImpl @Inject() (implicit akkaRuntime: AkkaRuntime)
  extends ComponentBase with RemoteMantikRegistry with FailFastCirceSupport {

  private val configRoot = config.getConfig("mantik.core.registry")
  private val url = configRoot.getString("url")

  private val user = configRoot.getString("username")
  private val password = new SecretReader("password", configRoot)

  private val executor: ApiClient.RequestExecutor = { request =>
    val t0 = System.currentTimeMillis()
    Http().singleRequest(request).andThen {
      case Success(response) =>
        val t1 = System.currentTimeMillis()
        logger.debug(s"Calling ${request.method.name()} ${request.uri} ${response.status.intValue()} within ${t1 - t0}ms")
    }
  }
  private val api = new MantikRegistryApi(url, executor)
  private val tokenProvider = new MantikRegistryTokenProvider(api, user, password)

  /** Provides a token. */
  def token(): Future[String] = tokenProvider.getToken()

  override def get(mantikId: MantikId): Future[MantikArtifact] = {
    errorHandling {
      for {
        token <- tokenProvider.getToken()
        artifactResponse <- api.artifact(token, mantikId)
        artifact <- Future.fromTry(decodeMantikArtifact(mantikId, artifactResponse))
      } yield artifact
    }
  }

  override def ensureMantikId(itemId: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
    errorHandling {
      for {
        token <- tokenProvider.getToken()
        response <- api.tag(token, ApiTagRequest(itemId, mantikId))
      } yield response.updated
    }
  }

  private def decodeMantikArtifact(mantikId: MantikId, apiGetArtifactResponse: ApiGetArtifactResponse): Try[MantikArtifact] = {
    for {
      _ <- Mantikfile.fromYaml(apiGetArtifactResponse.mantikDefinition).toTry
    } yield {
      MantikArtifact(
        apiGetArtifactResponse.mantikDefinition,
        fileId = apiGetArtifactResponse.fileId,
        namedId = apiGetArtifactResponse.namedId,
        itemId = apiGetArtifactResponse.itemId
      )
    }
  }

  private def errorHandling[T](f: => Future[T]): Future[T] = {
    f.recoverWith {
      case e: Errors.RepositoryError => Future.failed(e) // already mapped
      case f @ MantikRegistryApi.WrappedError(e) =>
        e.code match {
          case x if x == ApiErrorResponse.NotFound => Future.failed(new Errors.NotFoundException(e.message.getOrElse("")))
          case _                                   => Future.failed(f)
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
            namedId = mantikArtifact.namedId,
            itemId = mantikArtifact.itemId,
            mantikfile = mantikArtifact.mantikfile,
            hasFile = payload.nonEmpty
          )
        )
        remoteFileId <- payload match {
          case Some((contentType, source)) =>
            // Akka HTTP Crashes on empty Chunks.
            val withoutEmptyChunks = source.filter(_.nonEmpty)
            api.uploadFile(
              token, mantikArtifact.itemId.toString, contentType, withoutEmptyChunks
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

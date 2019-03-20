package ai.mantik.executor.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ai.mantik.executor.model.{ Job, JobStatus }
import ai.mantik.executor.{ Errors, Executor }
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }

/** Akka based client for the Executor. */
class ExecutorClient(url: Uri)(implicit actorSystem: ActorSystem, mat: Materializer) extends Executor with FailFastCirceSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  private implicit def ec: ExecutionContext = actorSystem.dispatcher
  private val http = Http()

  override def schedule(job: Job): Future[String] = {
    val req = buildRequest(HttpMethods.POST, "schedule")
    for {
      entity <- Marshal(job).to[RequestEntity]
      response <- executeRequest[String](req.withEntity(entity))
    } yield response
  }

  override def status(isolationSpace: String, id: String): Future[JobStatus] = {
    val req = buildRequest(HttpMethods.GET, "status", Seq("isolationSpace" -> isolationSpace, "id" -> id))
    executeRequest[JobStatus](req)
  }

  override def logs(isolationSpace: String, id: String): Future[String] = {
    val req = buildRequest(HttpMethods.GET, "logs", Seq("isolationSpace" -> isolationSpace, "id" -> id))
    executeRequest[String](req)
  }

  private def buildRequest(method: HttpMethod, path: String, queryArgs: Seq[(String, String)] = Nil): HttpRequest = {
    HttpRequest(method = method, uri = Uri(path)
      .resolvedAgainst(url)
      .withQuery(Uri.Query.apply(queryArgs: _*))
    )
  }

  private def executeRequest[T](req: HttpRequest)(implicit u: Unmarshaller[HttpResponse, T]): Future[T] = {
    val name = s"${req.method.value} ${req.uri}"
    logger.debug(s"Executing request $name (${req.entity.contentType})")
    http.singleRequest(req).flatMap { response =>
      logger.debug(s"Request response $name: ${response.status.intValue()} (${response.entity.contentType})")
      if (response.status.isSuccess()) {
        Unmarshal(response).to[T]
      } else {
        Unmarshal(response).to[Errors.ExecutorException].flatMap { e =>
          Future.failed(e)
        }
      }
    }
  }
}

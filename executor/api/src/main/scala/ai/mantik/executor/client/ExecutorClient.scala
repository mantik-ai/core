package ai.mantik.executor.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{ Marshal, Marshaller }
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ai.mantik.executor.model.{ DeployServiceRequest, DeployServiceResponse, DeployedServicesQuery, DeployedServicesResponse, Job, JobStatus, PublishServiceRequest, PublishServiceResponse }
import ai.mantik.executor.{ Errors, Executor }
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }

/** Akka based client for the Executor. */
class ExecutorClient(url: Uri)(implicit actorSystem: ActorSystem, mat: Materializer) extends Executor with FailFastCirceSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  private implicit def ec: ExecutionContext = actorSystem.dispatcher
  private val http = Http()

  override def schedule(job: Job): Future[String] = {
    simplePost[Job, String]("schedule", job)
  }

  override def status(isolationSpace: String, id: String): Future[JobStatus] = {
    val req = buildRequest(HttpMethods.GET, "status", Seq("isolationSpace" -> isolationSpace, "id" -> id))
    executeRequest[JobStatus](req)
  }

  override def logs(isolationSpace: String, id: String): Future[String] = {
    val req = buildRequest(HttpMethods.GET, "logs", Seq("isolationSpace" -> isolationSpace, "id" -> id))
    executeRequest[String](req)
  }

  override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
    simplePost[PublishServiceRequest, PublishServiceResponse]("publishService", publishServiceRequest)
  }

  override def deployService(deployServiceRequest: DeployServiceRequest): Future[DeployServiceResponse] = {
    simplePost[DeployServiceRequest, DeployServiceResponse]("deployments", deployServiceRequest)
  }

  override def queryDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[DeployedServicesResponse] = {
    val req = buildRequest(HttpMethods.GET, "deployments", deployedServicesQuery.toQueryParameters)
    executeRequest[DeployedServicesResponse](req)
  }

  override def deleteDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[Int] = {
    val req = buildRequest(HttpMethods.DELETE, "deployments", deployedServicesQuery.toQueryParameters)
    executeRequest[Int](req)
  }

  private def buildRequest(method: HttpMethod, path: String, queryArgs: Seq[(String, String)] = Nil): HttpRequest = {
    HttpRequest(method = method, uri = Uri(path)
      .resolvedAgainst(url)
      .withQuery(Uri.Query.apply(queryArgs: _*))
    )
  }

  /** Executes a simple post request with input and output structure. */
  private def simplePost[In, Out](path: String, in: In)(
    implicit
    marshaller: Marshaller[In, RequestEntity],
    unmarshaller: Unmarshaller[HttpResponse, Out]
  ): Future[Out] = {
    val req = buildRequest(HttpMethods.POST, path)
    for {
      entity <- Marshal(in).to[RequestEntity]
      response <- executeRequest[Out](req.withEntity(entity))
    } yield response
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

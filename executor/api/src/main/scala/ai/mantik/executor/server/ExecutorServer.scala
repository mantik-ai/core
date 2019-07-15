package ai.mantik.executor.server
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{ Marshal, ToResponseMarshallable }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ai.mantik.executor.{ Errors, Executor }
import ai.mantik.executor.model.{ DeployServiceRequest, DeployedServicesQuery, Job, PublishServiceRequest }
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.concurrent.duration._

/** Makes the Executor Interface reachable via HTTP. */
class ExecutorServer(
    config: ServerConfig,
    executor: Executor)(implicit actorSystem: ActorSystem, materializer: Materializer) extends FailFastCirceSupport {

  val logger = LoggerFactory.getLogger(getClass)
  import materializer.executionContext

  implicit def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: Errors.ExecutorException =>
      logger.warn("Executor exception", e)
      complete(e.statusCode, e)
    case NonFatal(exc) =>
      logger.error("Internal exception", exc)
      val e: Errors.ExecutorException = new Errors.InternalException("Internal error, please check logs")
      complete(e.statusCode, e)
  }

  val route =
    concat(
      postRoute("schedule") {
        executor.schedule
      },
      path("status") {
        get {
          parameters('isolationSpace, 'id) { (isolationSpace, jobId) =>
            onSuccess(executor.status(isolationSpace, jobId)) { status =>
              complete(status)
            }
          }
        }
      },
      path("logs") {
        get {
          parameters('isolationSpace, 'id) { (isolationSpace, jobId) =>
            onSuccess(executor.logs(isolationSpace, jobId)) { logs =>
              complete(logs)
            }
          }
        }
      },
      postRoute("publishService") {
        executor.publishService
      },
      path("deployments") {
        concat(
          post {
            entity(as[DeployServiceRequest]) { request =>
              callService(request)(executor.deployService)
            }
          },
          get {
            parameterMap { parameters =>
              val query = DeployedServicesQuery.fromQueryParameters(parameters) match {
                case Left(error) => throw new IllegalArgumentException(error)
                case Right(ok)   => ok
              }
              callService(query)(executor.queryDeployedServices)
            }
          },
          delete {
            parameterMap { parameters =>
              val query = DeployedServicesQuery.fromQueryParameters(parameters) match {
                case Left(error) => throw new IllegalArgumentException(error)
                case Right(ok)   => ok
              }
              callService(query)(executor.deleteDeployedServices)
            }
          }
        )
      },
      path("") {
        get {
          complete("Executor Server, backend: " + executor.nameAndVersion)
        }
      },
      path("version") {
        get {
          complete(executor.nameAndVersion)
        }
      }
    )

  private var openServerBinding: Option[Http.ServerBinding] = None

  /** A Simple POST call which forwards calls to a handler. */
  private def postRoute[In: FromRequestUnmarshaller, Out](
    routePath: String
  )(handler: In => Future[Out])(implicit f: Out => ToResponseMarshallable) = path(routePath) {
    post {
      entity(as[In]) { request =>
        callService(request)(handler)
      }
    }
  }

  private def callService[In, Out](in: In)(handler: In => Future[Out])(implicit f: Out => ToResponseMarshallable) = {
    onSuccess(handler(in)) { response =>
      complete(response)
    }
  }

  // Timeout for initializing and de-initializing HTTP Server
  private val HttpUpDownTimeout = 60.seconds

  /** Start the server (not threadsafe) */
  def start(): Unit = {
    val bindingFuture = Http().bindAndHandle(route, config.interface, config.port)
    val result = Await.result(bindingFuture, HttpUpDownTimeout)
    logger.info(s"Listening on ${config.interface}:${config.port}")
    require(openServerBinding.isEmpty)
    openServerBinding = Some(result)
  }

  /** Stop the server (not threadsafe) */
  def stop(): Unit = {
    require(openServerBinding.isDefined)
    openServerBinding.foreach { binding =>
      Await.result(binding.terminate(HttpUpDownTimeout), HttpUpDownTimeout)
    }
    openServerBinding = None
  }
}

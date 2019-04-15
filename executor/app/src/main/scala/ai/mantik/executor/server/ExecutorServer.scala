package ai.mantik.executor.server
import ai.mantik.executor.buildinfo.BuildInfo
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpResponse, ResponseEntity }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.Materializer
import com.typesafe.scalalogging.Logger
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ai.mantik.executor.{ Config, Errors, Executor }
import ai.mantik.executor.model.{ Job, PublishServiceRequest }

import scala.concurrent.{ Await, ExecutionContext }
import scala.util.control.NonFatal

class ExecutorServer(config: Config, executor: Executor)(implicit actorSystem: ActorSystem, materializer: Materializer) extends FailFastCirceSupport {

  val logger = Logger(getClass)
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
      path("schedule") {
        post {
          entity(as[Job]) { job =>
            onSuccess(executor.schedule(job)) { jobId =>
              complete(jobId)
            }
          }
        }
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
      path("publishService") {
        post {
          entity(as[PublishServiceRequest]) { request =>
            onSuccess(executor.publishService(request)) { response =>
              complete(response)
            }
          }
        }
      },
      path("") {
        get {
          complete(s"Mantik Executor ${BuildInfo.version}  (${BuildInfo.gitVersion}-${BuildInfo.buildNum})")
        }
      }
    )

  private var openServerBinding: Option[Http.ServerBinding] = None

  /** Start the server (not threadsafe) */
  def start(): Unit = {
    val bindingFuture = Http().bindAndHandle(route, config.interface, config.port)
    val result = Await.result(bindingFuture, config.defaultTimeout)
    logger.info(s"Listening on ${config.interface}:${config.port}")
    require(openServerBinding.isEmpty)
    openServerBinding = Some(result)
  }

  /** Stop the server (not threadsafe) */
  def stop(): Unit = {
    require(openServerBinding.isDefined)
    openServerBinding.foreach { binding =>
      Await.result(binding.terminate(config.defaultTimeout), config.defaultTimeout)
    }
    openServerBinding = None
  }
}

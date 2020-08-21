package ai.mantik.executor.server
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.Errors.ExecutorException
import ai.mantik.executor.{ Executor, ExecutorApi }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import net.reactivecore.fhttp.akka.{ ApiServerRoute, RouteBuilder }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

/** Makes the Executor Interface reachable via HTTP. */
class ExecutorServer(
    config: ServerConfig,
    executor: Executor)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {

  private def bindErrors[T](f: Future[T]): Future[Either[(Int, ExecutorException), T]] = {
    f.map { result =>
      Right(result)
    }.recover {
      case e: ExecutorException =>
        Left(e.statusCode -> e)
    }
  }

  val route = new ApiServerRoute {

    bind(ExecutorApi.publishService).to { req =>
      bindErrors(executor.publishService(req))
    }

    bind(ExecutorApi.nameAndVersion).to { _ =>
      bindErrors(executor.nameAndVersion)
    }

    bind(ExecutorApi.grpcProxy).to { isolationSpace =>
      bindErrors(executor.grpcProxy(isolationSpace))
    }

    bind(ExecutorApi.startWorker).to { req =>
      bindErrors(executor.startWorker(req))
    }

    bind(ExecutorApi.listWorker).to { req =>
      bindErrors(executor.listWorkers(req))
    }

    bind(ExecutorApi.stopWorker).to { req =>
      bindErrors(executor.stopWorker(req))
    }

    add {
      path("") {
        get {
          onSuccess(executor.nameAndVersion) { version =>
            complete("Executor Server, backend: " + version)
          }
        }
      }
    }
  }

  private var openServerBinding: Option[Http.ServerBinding] = None

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

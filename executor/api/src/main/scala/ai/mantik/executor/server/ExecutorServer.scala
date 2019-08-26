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

    bind(ExecutorApi.schedule).to { job =>
      bindErrors(executor.schedule(job))
    }

    bind(ExecutorApi.status).to {
      case (isolationSpace, jobId) =>
        bindErrors(executor.status(isolationSpace, jobId))
    }

    bind(ExecutorApi.logs).to {
      case (isolationSpace, jobId) =>
        bindErrors(executor.logs(isolationSpace, jobId))
    }

    bind(ExecutorApi.publishService).to { req =>
      bindErrors(executor.publishService(req))
    }

    bind(ExecutorApi.deployService).to { req =>
      bindErrors(executor.deployService(req))
    }

    bind(ExecutorApi.queryDeployedService).to { req =>
      bindErrors(executor.queryDeployedServices(req))
    }

    bind(ExecutorApi.deleteDeployedServices).to { req =>
      bindErrors(executor.deleteDeployedServices(req))
    }

    bind(ExecutorApi.nameAndVersion).to { _ =>
      bindErrors(Future.successful(executor.nameAndVersion))
    }

    add {
      path("") {
        get {
          complete("Executor Server, backend: " + executor.nameAndVersion)
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

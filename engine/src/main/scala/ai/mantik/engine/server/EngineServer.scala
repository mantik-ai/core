package ai.mantik.engine.server

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.engine.protos.debug.DebugServiceGrpc
import ai.mantik.engine.protos.debug.DebugServiceGrpc.DebugService
import ai.mantik.engine.protos.engine.AboutServiceGrpc
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderService
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorService
import ai.mantik.engine.protos.local_registry.LocalRegistryServiceGrpc
import ai.mantik.engine.protos.local_registry.LocalRegistryServiceGrpc.LocalRegistryService
import ai.mantik.engine.protos.remote_registry.RemoteRegistryServiceGrpc
import ai.mantik.engine.protos.remote_registry.RemoteRegistryServiceGrpc.RemoteRegistryService
import ai.mantik.engine.protos.sessions.SessionServiceGrpc
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionService
import ai.mantik.executor.Executor
import ai.mantik.planner.protos.planning_context.PlanningContextServiceGrpc.PlanningContextService
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import javax.inject.Inject

import scala.concurrent.Future

class EngineServer @Inject() (
    aboutService: AboutService,
    sessionService: SessionService,
    graphBuilderService: GraphBuilderService,
    graphExecutorService: GraphExecutorService,
    debugService: DebugService,
    localRegistryService: LocalRegistryService,
    remoteRegistryService: RemoteRegistryService,
    remotePlanningContext: PlanningContextService,
    executor: Executor
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

  val port = config.getInt("mantik.engine.server.port")
  private val interface = config.getString("mantik.engine.server.interface")

  private var server: Option[Server] = None

  /** Start the server */
  def start(): Server = {
    if (server.isDefined) {
      throw new IllegalStateException("Server already running")
    }

    val instance = buildServer()
    this.server = Some(instance)
    logger.info(s"Starting server at ${interface}:${port}")
    instance.start()

    instance
  }

  /** Block the thread until the server is finished. */
  def waitUntilFinished(): Unit = {
    val instance = this.server.getOrElse {
      throw new IllegalStateException("Server not running")
    }
    instance.awaitTermination()
  }

  addShutdownHook {
    stop()
    Future.successful(())
  }

  def stop(): Unit = {
    if (this.server.isEmpty) {
      logger.info("Server not running, cancelling shutdown")
      return
    }
    logger.info(s"Requesting server shutdown")
    val instance = server.get
    instance.shutdown()
    if (!instance.awaitTermination(30, TimeUnit.SECONDS)) {
      logger.info("Forcing server shutdown")
      instance.shutdownNow()
      instance.awaitTermination()
    }
    logger.info("Server shut down")
    this.server = None
  }

  private def buildServer(): Server = {
    NettyServerBuilder
      .forAddress(new InetSocketAddress(interface, port))
      .addService(AboutServiceGrpc.bindService(aboutService, executionContext))
      .addService(SessionServiceGrpc.bindService(sessionService, executionContext))
      .addService(GraphBuilderServiceGrpc.bindService(graphBuilderService, executionContext))
      .addService(GraphExecutorServiceGrpc.bindService(graphExecutorService, executionContext))
      .addService(DebugServiceGrpc.bindService(debugService, executionContext))
      .addService(LocalRegistryServiceGrpc.bindService(localRegistryService, executionContext))
      .addService(RemoteRegistryServiceGrpc.bindService(remoteRegistryService, executionContext))
      .addService(PlanningContextService.bindService(remotePlanningContext, executionContext))
      .build()
  }

}

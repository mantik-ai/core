package ai.mantik.engine.server

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import ai.mantik.engine.protos.debug.DebugServiceGrpc
import ai.mantik.engine.protos.debug.DebugServiceGrpc.DebugService
import ai.mantik.engine.protos.engine.AboutServiceGrpc
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderService
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorService
import ai.mantik.engine.protos.sessions.SessionServiceGrpc
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionService
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc.FileRepositoryService
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryService
import com.typesafe.config.Config
import io.grpc.netty.NettyServerBuilder
import io.grpc.{ Server, ServerBuilder }
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

class EngineServer(
    aboutService: AboutService,
    sessionService: SessionService,
    graphBuilderService: GraphBuilderService,
    graphExecutorService: GraphExecutorService,
    debugService: DebugService,
    repositoryService: RepositoryService,
    fileRepositoryService: FileRepositoryService
)(implicit config: Config, executionContext: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(getClass)
  val port = config.getInt("mantik.engine.server.port")
  private val interface = config.getString("mantik.engine.server.interface")

  private var server: Option[Server] = None

  /** Start the server */
  def start(): Server = {
    if (server.isDefined) {
      throw new IllegalStateException("Server already running")
    }

    val instance = buildServer()
    logger.info("Starting server")
    this.server = Some(instance)
    logger.info(s"Starting server at ${interface}:${port}")
    instance.start()
  }

  /** Stop the server. */
  def stop(): Unit = {
    if (this.server.isEmpty) {
      throw new IllegalStateException("Server not running")
    }
    shutdown()
    this.server = None
  }

  /** Block the thread until the server is finished. */
  def waitUntilFinished(): Unit = {
    val instance = this.server.getOrElse {
      throw new IllegalStateException("Server not running")
    }
    instance.awaitTermination()
  }

  private def shutdown(): Unit = {
    logger.info(s"Requesting server shutdown")
    val instance = server.get
    instance.shutdown()
    if (!instance.awaitTermination(30, TimeUnit.SECONDS)) {
      logger.info("Forcing server shutdown")
      instance.shutdownNow()
      instance.awaitTermination()
    }
    logger.info("Server shut down")
  }

  private def buildServer(): Server = {
    NettyServerBuilder
      .forAddress(new InetSocketAddress(interface, port))
      .addService(AboutServiceGrpc.bindService(aboutService, executionContext))
      .addService(SessionServiceGrpc.bindService(sessionService, executionContext))
      .addService(GraphBuilderServiceGrpc.bindService(graphBuilderService, executionContext))
      .addService(GraphExecutorServiceGrpc.bindService(graphExecutorService, executionContext))
      .addService(DebugServiceGrpc.bindService(debugService, executionContext))
      .addService(RepositoryServiceGrpc.bindService(repositoryService, executionContext))
      .addService(FileRepositoryServiceGrpc.bindService(fileRepositoryService, executionContext))
      .build()
  }

}

package ai.mantik.engine

import ai.mantik.componently.{ AkkaRuntime, Component }
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutServiceBlockingStub
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderServiceBlockingStub
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorServiceBlockingStub
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionServiceBlockingStub
import ai.mantik.planner.Context
import ai.mantik.planner.impl.ContextImpl
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc.FileRepositoryServiceStub
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryServiceStub
import ai.mantik.planner.repository.rpc.{ FileRepositoryClientImpl, RepositoryClientImpl }
import com.google.protobuf.empty.Empty
import com.typesafe.scalalogging.Logger
import io.grpc.Status.Code
import io.grpc.{ ManagedChannelBuilder, StatusRuntimeException }

/** Talks to a local engine. */
class EngineClient(address: String)(implicit val akkaRuntime: AkkaRuntime) extends Component {
  val logger = Logger(getClass)
  logger.info(s"Connecting to Mantik Engine at ${address}")

  val channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build()

  val aboutService = new AboutServiceBlockingStub(channel)

  val version = try {
    aboutService.version(Empty())
  } catch {
    case e: StatusRuntimeException if e.getStatus.getCode == Code.UNAVAILABLE =>
      logger.error("Could not connect to Mantik Engine, is the service running?!")
      throw e
  }
  logger.info(s"Connected to Mantik Engine ${version.version}")

  val sessionService = new SessionServiceBlockingStub(channel)
  val graphBuilder = new GraphBuilderServiceBlockingStub(channel)
  val graphExecutor = new GraphExecutorServiceBlockingStub(channel)
  val repository = new RepositoryClientImpl(new RepositoryServiceStub(channel))
  val fileRepository = new FileRepositoryClientImpl(new FileRepositoryServiceStub(channel))
  val executor = ContextImpl.constructExecutorClient()

  val plannerContext: Context = ContextImpl.constructWithComponents(repository, fileRepository, executor)

  override def shutdown(): Unit = {
    plannerContext.shutdown()
    channel.shutdownNow()
  }
}

object EngineClient {
  def create()(implicit akkaRuntime: AkkaRuntime): EngineClient = {
    val port = akkaRuntime.config.getInt("mantik.engine.server.port")
    val interface = akkaRuntime.config.getString("mantik.engine.server.interface")
    val full = s"${interface}:${port}"
    new EngineClient(full)
  }
}
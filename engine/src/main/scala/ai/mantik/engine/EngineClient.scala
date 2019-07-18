package ai.mantik.engine

import ai.mantik.componently.di.AkkaModule
import ai.mantik.componently.{ AkkaRuntime, Component }
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutServiceBlockingStub
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderServiceBlockingStub
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorServiceBlockingStub
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionServiceBlockingStub
import ai.mantik.planner.Context
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc.{ FileRepositoryService, FileRepositoryServiceStub }
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.{ RepositoryService, RepositoryServiceStub }
import com.google.inject.{ AbstractModule, Guice }
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
  val repositoryServiceStub = new RepositoryServiceStub(channel)
  val fileRepositoryServiceStub = new FileRepositoryServiceStub(channel)

  /** Create a new context for Scala Applications. */
  def createContext(): Context = {
    val injector = Guice.createInjector(
      new AkkaModule(),
      new EngineModule(isClient = true),
      createClientServiceModule()
    )
    injector.getInstance(classOf[Context])
  }

  /** Create a Guice module which contains the needed gRpc clients to talk to the engine server. */
  private def createClientServiceModule(): AbstractModule = new AbstractModule {
    override def configure(): Unit = {
      bind(classOf[RepositoryService]).toInstance(repositoryServiceStub)
      bind(classOf[FileRepositoryService]).toInstance(fileRepositoryServiceStub)
    }
  }

  override def shutdown(): Unit = {
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
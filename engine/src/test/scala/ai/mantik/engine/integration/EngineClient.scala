package ai.mantik.engine.integration

import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutServiceBlockingStub
import ai.mantik.engine.protos.graph_builder.GraphBuilderServiceGrpc.GraphBuilderServiceBlockingStub
import ai.mantik.engine.protos.graph_executor.GraphExecutorServiceGrpc.GraphExecutorServiceBlockingStub
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionServiceBlockingStub
import io.grpc.ManagedChannelBuilder

class EngineClient(address: String) {

  val channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build()

  val aboutService = new AboutServiceBlockingStub(channel)
  val sessionService = new SessionServiceBlockingStub(channel)
  val graphBuilder = new GraphBuilderServiceBlockingStub(channel)
  val graphExecutor = new GraphExecutorServiceBlockingStub(channel)

  def shutdown(): Unit = {
    channel.shutdownNow()
  }
}

package ai.mantik.planner.repository.rpc

import java.net.InetSocketAddress

import io.grpc.netty.NettyServerBuilder
import io.grpc.{ ManagedChannelBuilder, ServerServiceDefinition }

/** Helper for testing gRpc services. */
class RpcTestConnection(services: ServerServiceDefinition*) {

  val grpcServer = {
    val builder = NettyServerBuilder
      .forAddress(new InetSocketAddress("localhost", 0))

    val withServices = services.foldLeft(builder)((c, n) => c.addService(n))
    withServices.build()
  }
  grpcServer.start()

  val channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.getPort).usePlaintext().build()

  def shutdown(): Unit = {
    grpcServer.shutdownNow()
  }

}

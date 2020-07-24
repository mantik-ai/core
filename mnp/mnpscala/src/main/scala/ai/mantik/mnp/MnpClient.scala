package ai.mantik.mnp

import java.net.{ MalformedURLException, SocketAddress, URL }

import ai.mantik.mnp.protocol.mnp.{ AboutResponse, ConfigureInputPort, ConfigureOutputPort, InitRequest, InitResponse, QuitRequest, QuitResponse, SessionState }
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.{ MnpService, MnpServiceStub }
import com.google.protobuf.any.Any
import com.google.protobuf.empty.Empty
import io.grpc.{ HttpConnectProxiedSocketAddress, ManagedChannel, ManagedChannelBuilder, ProxiedSocketAddress, ProxyDetector }
import io.grpc.stub.StreamObserver
import scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }

import scala.concurrent.{ ExecutionContext, Future, Promise }

/** Small shim on top of MnpService to make it better usable. */
class MnpClient(val address: String, mnpService: MnpService) {

  def about(): Future[AboutResponse] = {
    mnpService.about(Empty())
  }

  def quit(): Future[QuitResponse] = {
    mnpService.quit(QuitRequest())
  }

  /**
   * Initialize a new MNP Session.
   * Can throw [[SessionInitException]] when the init fails on remote side
   */
  def initSession[T <: GeneratedMessage](
    sessionId: String,
    config: Option[T],
    inputs: Seq[ConfigureInputPort],
    outputs: Seq[ConfigureOutputPort],
    callback: SessionState => Unit = s => {}
  ): Future[MnpSession] = {
    val serializedConfig = config.map { config =>
      Any.pack(config)
    }
    val initRequest = InitRequest(
      sessionId,
      serializedConfig,
      inputs,
      outputs
    )

    val resultPromise = Promise[MnpSession]

    object waiter extends StreamObserver[InitResponse] {
      override def onNext(value: InitResponse): Unit = {
        value.state match {
          case SessionState.SS_READY =>
            resultPromise.trySuccess(
              new MnpSession(address, sessionId, mnpService)
            )
          case SessionState.SS_FAILED =>
            resultPromise.tryFailure(
              new SessionInitException(value.error)
            )
          case other =>
            callback(other)
        }
      }

      override def onError(t: Throwable): Unit = {
        resultPromise.tryFailure(t)
      }

      override def onCompleted(): Unit = {
        if (!resultPromise.isCompleted) {
          resultPromise.tryFailure(new ProtocolException("Stream completed without reply"))
        }
      }
    }

    mnpService.init(initRequest, waiter)

    resultPromise.future
  }

}

object MnpClient {

  def forChannel(address: String, channel: ManagedChannel): MnpClient = {
    val serviceStub = new MnpServiceStub(channel)
    val client = new MnpClient(address, serviceStub)
    client
  }

  def connect(address: String): (ManagedChannel, MnpClient) = {
    val channel: ManagedChannel = ManagedChannelBuilder
      .forTarget(address)
      .usePlaintext().build()
    (channel, forChannel(address, channel))
  }

  @throws[MalformedURLException]("For bad Proxy URLs")
  def connectViaProxy(proxy: String, address: String): (ManagedChannel, MnpClient) = {
    val proxyUrlParsed = new URL(proxy)
    val proxyDetector = new MnpProxyDetector(proxyUrlParsed)
    val channel: ManagedChannel = ManagedChannelBuilder
      .forTarget(address)
      .proxyDetector(proxyDetector)
      .usePlaintext().build()
    (channel, forChannel(address, channel))
  }
}

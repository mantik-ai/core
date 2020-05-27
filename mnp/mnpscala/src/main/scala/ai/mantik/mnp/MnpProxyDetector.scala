package ai.mantik.mnp

import java.net.{ InetSocketAddress, SocketAddress, URL }

import io.grpc.{ HttpConnectProxiedSocketAddress, ProxiedSocketAddress, ProxyDetector }

/** Helper for tunneling MNP via Proxy */
class MnpProxyDetector(proxyUrl: URL) extends ProxyDetector {

  private val port = if (proxyUrl.getPort < 0) {
    proxyUrl.getDefaultPort
  } else {
    proxyUrl.getPort
  }

  private val proxyAddress = new InetSocketAddress(
    proxyUrl.getHost, port
  )

  override def proxyFor(targetServerAddress: SocketAddress): ProxiedSocketAddress = {
    targetServerAddress match {
      case i: InetSocketAddress =>
        HttpConnectProxiedSocketAddress.newBuilder
          .setProxyAddress(proxyAddress)
          .setTargetAddress(i)
          .build
      case _ => null
    }
  }
}

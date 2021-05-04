/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.mnp

import java.net.{InetSocketAddress, SocketAddress, URL}

import io.grpc.{HttpConnectProxiedSocketAddress, ProxiedSocketAddress, ProxyDetector}

/** Helper for tunneling MNP via Proxy */
class MnpProxyDetector(proxyUrl: URL) extends ProxyDetector {

  private val port = if (proxyUrl.getPort < 0) {
    proxyUrl.getDefaultPort
  } else {
    proxyUrl.getPort
  }

  private val proxyAddress = new InetSocketAddress(
    proxyUrl.getHost,
    port
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

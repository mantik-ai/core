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

import java.net.{MalformedURLException, SocketAddress, URL}

import ai.mantik.mnp.protocol.mnp.{
  AboutResponse,
  ConfigureInputPort,
  ConfigureOutputPort,
  InitRequest,
  InitResponse,
  QuitRequest,
  QuitResponse,
  SessionState
}
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.{MnpService, MnpServiceStub}
import com.google.protobuf.any.Any
import com.google.protobuf.empty.Empty
import io.grpc.{
  HttpConnectProxiedSocketAddress,
  ManagedChannel,
  ManagedChannelBuilder,
  ProxiedSocketAddress,
  ProxyDetector
}
import io.grpc.stub.StreamObserver
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}

import scala.concurrent.{ExecutionContext, Future, Promise}

/** Small shim on top of MnpService to make it better usable.
  * @param address adress of the node (for constructing URLs)
  * @param mnpService gRpc Service
  * @param channel gRpc Managed Channel (for shutdown)
  */
class MnpClient(val address: String, mnpService: MnpService, val channel: ManagedChannel) {

  def about(): Future[AboutResponse] = {
    mnpService.about(Empty())
  }

  def quit(): Future[QuitResponse] = {
    mnpService.quit(QuitRequest())
  }

  /** Get a session object for a given session id, assuming it exists */
  def joinSession(sessionId: String): MnpSession = {
    new MnpSession(address, sessionId, mnpService)
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

    val resultPromise = Promise[MnpSession]()

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
    val client = new MnpClient(address, serviceStub, channel)
    client
  }

  def connect(address: String): MnpClient = {
    val channel: ManagedChannel = ManagedChannelBuilder
      .forTarget(address)
      .usePlaintext()
      .build()
    forChannel(address, channel)
  }

  @throws[MalformedURLException]("For bad Proxy URLs")
  def connectViaProxy(proxy: String, address: String): MnpClient = {
    val proxyUrlParsed = new URL(proxy)
    val proxyDetector = new MnpProxyDetector(proxyUrlParsed)
    val channel: ManagedChannel = ManagedChannelBuilder
      .forTarget(address)
      .proxyDetector(proxyDetector)
      .usePlaintext()
      .build()
    forChannel(address, channel)
  }
}

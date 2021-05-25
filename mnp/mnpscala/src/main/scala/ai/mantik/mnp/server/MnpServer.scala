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
package ai.mantik.mnp.server

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.MnpService
import io.grpc.{Server, ServerBuilder}
import io.grpc.netty.NettyServerBuilder

import java.net.InetSocketAddress
import scala.concurrent.Future

/**
  * A Server for the MNP Protocol
  * @param interface network interface to use
  * @param chosenPort port to choose (0 for random port)
  */
class MnpServer(
    backend: ServerBackend,
    interface: String = "127.0.0.1",
    chosenPort: Int = 0
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

  private val server: Server = buildServer()

  /** Run the server (blocking) */
  def start(): Unit = {
    server.start()
  }

  /** Return the used TCP port */
  def port: Int = {
    server.getPort
  }

  /** Returns the address in the form interface:port */
  def address: String = {
    s"${interface}:${port}"
  }

  /** Wait for termination */
  def awaitTermination(): Unit = {
    server.awaitTermination()
  }

  /** Terminate the MNP Server */
  def stop(): Unit = {
    server.shutdown()
  }

  /** Server is in process of shutting down or already shut down */
  def isShutdown: Boolean = {
    server.isShutdown
  }

  addShutdownHook {
    Future {
      stop()
    }
  }

  private def buildServer(): Server = {
    val mnpService = new MnpServiceImp(backend, { () => stop() })
    NettyServerBuilder
      .forAddress(new InetSocketAddress(interface, chosenPort))
      .addService(MnpServiceGrpc.bindService(mnpService, executionContext))
      .build()
  }
}

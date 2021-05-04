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
package ai.mantik.planner.integration

import ai.mantik.executor.model.{MnpWorkerDefinition, StartWorkerRequest}
import ai.mantik.executor.model.docker.Container
import ai.mantik.mnp.MnpClient
import ai.mantik.planner.BuiltInItems

/** Test that we can communicate with an MNP Bridge. */
class HelloMnpBridgeSpec extends IntegrationTestBase {

  private def executor = embeddedExecutor.executor
  private val isolationSpace = "hello-mnp-bridge"
  private val MnpPort = 8502

  it should "initialize" in {
    val startResponse = await(
      executor.startWorker(
        StartWorkerRequest(
          isolationSpace,
          "id1",
          MnpWorkerDefinition(
            Container(
              BuiltInItems.SelectBridge.mantikHeader.definition.dockerImage
            )
          )
        )
      )
    )
    logger.info(s"Started container ${startResponse.nodeName}")

    val grpcProxy = await(executor.grpcProxy(isolationSpace))
    logger.info(s"gRpc Proxy: ${grpcProxy}")

    val destinationAddress = s"${startResponse.nodeName}:${MnpPort}"

    Thread.sleep(2000) // Some time ot make it available

    val (channel, client) = grpcProxy.proxyUrl match {
      case Some(defined) =>
        MnpClient.connectViaProxy(defined, destinationAddress)
      case None =>
        MnpClient.connect(destinationAddress)
    }

    try {
      eventually {
        val response = await(client.about())
        response.name shouldNot be(empty)
      }
    } finally {
      channel.shutdownNow()
    }
  }
}

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
package ai.mantik.executor.common.test.integration

import java.util.Base64
import ai.mantik.executor.Executor
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{
  ListWorkerRequest,
  MnpPipelineDefinition,
  MnpWorkerDefinition,
  StartWorkerRequest,
  StartWorkerResponse,
  StopWorkerRequest,
  WorkerState,
  WorkerType
}
import ai.mantik.mnp.MnpAddressUrl
import ai.mantik.testutils.TestBase
import akka.util.ByteString

trait StartWorkerSpecBase {
  self: IntegrationBase with TestBase =>

  // user Id given to all containers
  val userId = "userId"

  protected def checkEmptyNow(): Unit = {
    // Addional check for implementors
  }

  protected def checkExistence(
      executor: Executor,
      startWorkerResponse: StartWorkerResponse,
      expectedType: WorkerType
  ): Unit = {
    val listResponse = await(
      executor.listWorkers(
        ListWorkerRequest(
        )
      )
    )

    val elements = listResponse.workers.filter(_.nodeName == startWorkerResponse.nodeName)
    withClue(s"There should be exactly one worker of node name ${startWorkerResponse.nodeName} in ${elements}") {
      elements.size shouldBe 1
    }
    val element = elements.head

    element.`type` shouldBe expectedType
    element.externalUrl shouldBe startWorkerResponse.externalUrl
  }

  protected def stopAndKill(executor: Executor, startWorkerResponse: StartWorkerResponse): Unit = {
    val stopResponse = await(
      executor.stopWorker(
        StopWorkerRequest(
          nameFilter = Some(startWorkerResponse.nodeName),
          remove = false
        )
      )
    )
    stopResponse.removed shouldNot be(empty)

    eventually {
      val listResponse2 = await(executor.listWorkers(ListWorkerRequest()))
      listResponse2.workers.find(_.nodeName == startWorkerResponse.nodeName).get.state.isTerminal shouldBe true
    }

    val stopResponse2 = await(
      executor.stopWorker(
        StopWorkerRequest(
          nameFilter = Some(startWorkerResponse.nodeName),
          remove = true
        )
      )
    )
    stopResponse2.removed shouldNot be(empty)

    eventually {
      val listResponse2 = await(executor.listWorkers(ListWorkerRequest()))
      listResponse2.workers.find(_.nodeName == startWorkerResponse.nodeName) shouldBe empty
    }

    checkEmptyNow()
  }

  private val simpleStartWorker = StartWorkerRequest(
    id = userId,
    definition = MnpWorkerDefinition(
      container = Container(
        image = "mantikai/bridge.binary"
      )
    )
  )

  private val pipelineRequest = StartWorkerRequest(
    id = userId,
    definition = MnpPipelineDefinition(
      io.circe.parser
        .parse("""{
                 |  "name": "my_pipeline",
                 |  "steps": [],
                 |  "inputType": "int32"
                 |}
                 |""".stripMargin)
        .forceRight
    )
  )

  it should "allow running a simple worker" in withExecutor { executor =>
    val response = await(executor.startWorker(simpleStartWorker))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe empty
    response.internalUrl shouldNot be(empty)
    response.internalUrl should startWith("mnp://")

    checkExistence(executor, response, WorkerType.MnpWorker)
    val client = await(executor.connectMnp(MnpAddressUrl.parse(response.internalUrl).forceRight.address))
    await(client.about()).name shouldNot be(empty)
    client.channel.shutdownNow()

    stopAndKill(executor, response)
  }

  it should "allow running a simple worker with name hint" in withExecutor { executor =>
    val nameHint = "name1"
    val response = await(executor.startWorker(simpleStartWorker.copy(nameHint = Some(nameHint))))
    response.nodeName shouldNot be(empty)
    response.nodeName should include(nameHint)

    checkExistence(executor, response, WorkerType.MnpWorker)

    stopAndKill(executor, response)
  }

  it should "allow deploying a persistent worker with initializer" in withExecutor { executor =>
    val startWorkerRequest = StartWorkerRequest(
      id = userId,
      definition = MnpWorkerDefinition(
        container = Container(
          image = "mantikai/bridge.select"
        ),
        initializer = Some(TestData.selectInitRequest)
      ),
      keepRunning = true
    )

    val response = await(executor.startWorker(startWorkerRequest))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe empty

    checkExistence(executor, response, WorkerType.MnpWorker)

    stopAndKill(executor, response)
  }

  it should "allow deploying a pipeline" in withExecutor { executor =>
    val response = await(executor.startWorker(pipelineRequest))
    response.nodeName shouldNot be(empty)
    response.internalUrl shouldNot be(empty)
    response.internalUrl should startWith("http://")
    response.externalUrl shouldBe empty

    checkExistence(executor, response, WorkerType.MnpPipeline)

    stopAndKill(executor, response)
  }

  it should "allow deploying a persistent pipeline" in withExecutor { executor =>
    val response = await(
      executor.startWorker(
        pipelineRequest.copy(
          keepRunning = true
        )
      )
    )
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe empty

    checkExistence(executor, response, WorkerType.MnpPipeline)

    stopAndKill(executor, response)
  }

  it should "allow deplying a persistent pipeline with ingress" in withExecutor { executor =>
    val response = await(
      executor.startWorker(
        pipelineRequest.copy(
          ingressName = Some("pipe1")
        )
      )
    )
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldBe defined

    checkExistence(executor, response, WorkerType.MnpPipeline)

    stopAndKill(executor, response)
  }
}

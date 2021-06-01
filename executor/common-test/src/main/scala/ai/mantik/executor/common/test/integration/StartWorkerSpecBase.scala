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
import ai.mantik.testutils.TestBase
import akka.util.ByteString

trait StartWorkerSpecBase {
  self: IntegrationBase with TestBase =>

  val isolationSpace = "start-worker-test"
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
          isolationSpace
        )
      )
    )

    val element = listResponse.workers.find(_.nodeName == startWorkerResponse.nodeName)
    element shouldBe defined

    element.get.`type` shouldBe expectedType
    element.get.externalUrl shouldBe startWorkerResponse.externalUrl
  }

  protected def stopAndKill(executor: Executor, startWorkerResponse: StartWorkerResponse): Unit = {
    val stopResponse = await(
      executor.stopWorker(
        StopWorkerRequest(
          isolationSpace,
          nameFilter = Some(startWorkerResponse.nodeName),
          remove = false
        )
      )
    )
    stopResponse.removed shouldNot be(empty)

    eventually {
      val listResponse2 = await(executor.listWorkers(ListWorkerRequest(isolationSpace)))
      listResponse2.workers.find(_.nodeName == startWorkerResponse.nodeName).get.state shouldBe 'terminal
    }

    val stopResponse2 = await(
      executor.stopWorker(
        StopWorkerRequest(
          isolationSpace,
          nameFilter = Some(startWorkerResponse.nodeName),
          remove = true
        )
      )
    )
    stopResponse2.removed shouldNot be(empty)

    eventually {
      val listResponse2 = await(executor.listWorkers(ListWorkerRequest(isolationSpace)))
      listResponse2.workers.find(_.nodeName == startWorkerResponse.nodeName) shouldBe empty
    }

    checkEmptyNow()
  }

  private val simpleStartWorker = StartWorkerRequest(
    isolationSpace = isolationSpace,
    id = userId,
    definition = MnpWorkerDefinition(
      container = Container(
        image = "mantikai/bridge.binary"
      )
    )
  )

  private val pipelineRequest = StartWorkerRequest(
    id = userId,
    isolationSpace = isolationSpace,
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

    checkExistence(executor, response, WorkerType.MnpWorker)

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
      isolationSpace = isolationSpace,
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

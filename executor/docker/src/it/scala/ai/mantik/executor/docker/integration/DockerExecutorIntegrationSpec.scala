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
package ai.mantik.executor.docker.integration

import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.common.test.integration.TestData
import ai.mantik.executor.common.workerexec.model._
import ai.mantik.executor.docker.api.structures.ListContainerRequestFilter
import ai.mantik.executor.docker.{DockerConstants, DockerWorkerExecutorBackend, DockerExecutorConfig}
import ai.mantik.executor.model.docker.Container
import io.circe.syntax._

class DockerExecutorIntegrationSpec extends IntegrationTestBase {

  trait Env {
    val config = DockerExecutorConfig.fromTypesafeConfig(typesafeConfig)
    val dockerExecutor = new DockerWorkerExecutorBackend(dockerClient, config)
  }

  trait EnvForWorkers extends Env {
    def startWorker(id: String): StartWorkerResponse = {
      val startWorkerRequest = StartWorkerRequest(
        id = id,
        definition = MnpWorkerDefinition(
          container = Container(
            image = "mantikai/bridge.binary"
          )
        )
      )
      await(dockerExecutor.startWorker(startWorkerRequest))
    }
  }

  "startWorker" should "should work" in new EnvForWorkers {
    val response = startWorker("foo")
    response.nodeName shouldNot be(empty)
    val containers = await(dockerClient.listContainers(true))
    val container = containers.find(_.Names.contains("/" + response.nodeName)).getOrElse(fail())
    container.Labels(DockerConstants.IsolationSpaceLabelName) shouldBe config.common.isolationSpace
    container.Labels(LabelConstants.UserIdLabelName) shouldBe "foo"
    container.Labels(LabelConstants.ManagedByLabelName) shouldBe LabelConstants.ManagedByLabelValue
    container.Labels(LabelConstants.WorkerTypeLabelName) shouldBe LabelConstants.workerType.mnpWorker

    eventually {
      val containerAgain = await(
        dockerClient.listContainersFiltered(
          true,
          ListContainerRequestFilter.forLabelKeyValue(
            LabelConstants.UserIdLabelName -> "foo"
          )
        )
      )
      println(containerAgain.asJson)
      containerAgain.head.State shouldBe "running"
    }
  }

  it should "be possible to initialize an MNP Node directly" in new Env {
    val startWorkerRequest = StartWorkerRequest(
      id = "startme",
      definition = MnpWorkerDefinition(
        container = Container(
          image = "mantikai/bridge.select"
        ),
        initializer = Some(TestData.selectInitRequest)
      )
    )
    val response = await(dockerExecutor.startWorker(startWorkerRequest))
    response.nodeName shouldNot be(empty)
    val containers = await(dockerClient.listContainers(true))
    containers.find(_.Names.contains(s"/${response.nodeName}")) shouldBe defined
    val initContainer = containers.find(_.Names.contains(s"/${response.nodeName}_init"))
    initContainer shouldBe defined
    logger.info(s"InitContainer ${initContainer.get.asJson}")
    initContainer.get.State shouldBe "exited"

    withClue("It should not show up in listWorkers") {
      val response = await(dockerExecutor.listWorkers(ListWorkerRequest()))
      response.workers.count(_.id == "startme") shouldBe 1
    }
  }

  "listWorkers" should "work" in new EnvForWorkers {
    await(dockerExecutor.stopWorker(StopWorkerRequest())) // killing them all
    eventually {
      val response = await(dockerExecutor.listWorkers(ListWorkerRequest()))
      response.workers shouldBe empty
    }

    val startResponse1 = startWorker("x1")
    val startResponse2 = startWorker("x2")

    val response2 = await(dockerExecutor.listWorkers(ListWorkerRequest()))
    response2.workers.size shouldBe 2
    response2.workers.map(_.id) should contain theSameElementsAs Seq("x1", "x2")
    response2.workers.map(_.`type`) should contain theSameElementsAs Seq(WorkerType.MnpWorker, WorkerType.MnpWorker)

    // id filter
    val response3 = await(dockerExecutor.listWorkers(ListWorkerRequest(idFilter = Some("x1"))))
    response3.workers.size shouldBe 1
    response3.workers.head.id shouldBe "x1"

    // name filter
    val response4 = await(
      dockerExecutor.listWorkers(ListWorkerRequest(nameFilter = Some(startResponse2.nodeName)))
    )
    response4.workers.size shouldBe 1
    response4.workers.head.id shouldBe "x2"
  }

  "stopWorkers" should "work" in new EnvForWorkers {
    val container1 = startWorker("x1")
    val container2 = startWorker("x2")
    val container3 = startWorker("x3")

    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest(idFilter = Some("x1"))))
      listResponse.workers.head.state shouldBe WorkerState.Running
    }

    // by id
    await(dockerExecutor.stopWorker(StopWorkerRequest(idFilter = Some("x1"), remove = false)))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest(idFilter = Some("x1"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }

    // by name
    await(
      dockerExecutor.stopWorker(StopWorkerRequest(nameFilter = Some(container2.nodeName), remove = false))
    )
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest(idFilter = Some("x2"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }

    // by all
    await(dockerExecutor.stopWorker(StopWorkerRequest(remove = false)))
    eventually {
      val listResponse = await(dockerExecutor.listWorkers(ListWorkerRequest(idFilter = Some("x3"))))
      listResponse.workers.head.state shouldBe an[WorkerState.Failed]
    }
  }
}

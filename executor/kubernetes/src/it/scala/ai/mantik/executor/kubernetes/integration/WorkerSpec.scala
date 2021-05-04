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
package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.model.{
  ListWorkerRequest,
  MnpWorkerDefinition,
  StartWorkerRequest,
  StartWorkerResponse,
  StopWorkerRequest
}
import ai.mantik.executor.model.docker.Container

class WorkerSpec extends IntegrationTestBase {

  trait Env extends super.Env {
    def startWorker(id: String, isolationSpace: String): StartWorkerResponse = {
      val startWorkerRequest = StartWorkerRequest(
        isolationSpace = isolationSpace,
        id = id,
        definition = MnpWorkerDefinition(
          container = Container(
            image = "mantikai/bridge.binary"
          )
        )
      )
      await(executor.startWorker(startWorkerRequest))
    }
  }

  it should "start stop and list workers" in new Env {
    val isolationSpace = "startworker-test"
    // The same id can be used multiple times
    val worker1 = startWorker("id1", isolationSpace)
    val worker2 = startWorker("id1", isolationSpace)
    val worker3 = startWorker("id2", isolationSpace)
    val worker4 = startWorker("id2", isolationSpace)

    val workersResponse1 = await(
      executor.listWorkers(
        ListWorkerRequest(isolationSpace)
      )
    )
    workersResponse1.workers.size shouldBe 4
    workersResponse1.workers.map(_.id).distinct should contain theSameElementsAs Seq("id1", "id2")
    workersResponse1.workers.map(_.nodeName) should contain theSameElementsAs Seq(
      worker1.nodeName,
      worker2.nodeName,
      worker3.nodeName,
      worker4.nodeName
    )

    val workerResponseDifferentSpace = await(
      executor.listWorkers(
        ListWorkerRequest("other-space")
      )
    )
    workerResponseDifferentSpace.workers shouldBe empty

    val workerResponseById = await(
      executor.listWorkers(
        ListWorkerRequest(isolationSpace, idFilter = Some("id1"))
      )
    )
    workerResponseById.workers.map(_.nodeName) should contain theSameElementsAs Seq(
      worker1.nodeName,
      worker2.nodeName
    )

    val workerResponseByNameFilter = await(
      executor.listWorkers(
        ListWorkerRequest(isolationSpace, nameFilter = Some(worker1.nodeName))
      )
    )
    workerResponseByNameFilter.workers.ensuring(_.size == 1).head.nodeName shouldBe worker1.nodeName

    // doesn't affect, all there
    await(
      executor.stopWorker(
        StopWorkerRequest("other-space")
      )
    )
    await(executor.listWorkers(ListWorkerRequest(isolationSpace))).workers.size shouldBe 4

    // Kill by Id
    val removedById = await(executor.stopWorker(StopWorkerRequest(isolationSpace, idFilter = Some("id1"))))
    removedById.removed.size shouldBe 2
    removedById.removed.map(_.name) should contain theSameElementsAs Seq(worker1.nodeName, worker2.nodeName)
    removedById.removed.map(_.id).distinct shouldBe Seq("id1")

    await(executor.listWorkers(ListWorkerRequest(isolationSpace))).workers.size shouldBe 2

    // Kill by Name
    val removedByName =
      await(executor.stopWorker(StopWorkerRequest(isolationSpace, nameFilter = Some(worker3.nodeName))))
    removedByName.removed.size shouldBe 1
    removedByName.removed.head.name shouldBe worker3.nodeName
    removedByName.removed.head.id shouldBe "id2"

    await(executor.listWorkers(ListWorkerRequest(isolationSpace))).workers.size shouldBe 1

    // Kill everything
    val removeRest = await(executor.stopWorker(StopWorkerRequest(isolationSpace)))
    removeRest.removed.size shouldBe 1
    await(executor.listWorkers(ListWorkerRequest(isolationSpace))).workers.size shouldBe 0
  }
}

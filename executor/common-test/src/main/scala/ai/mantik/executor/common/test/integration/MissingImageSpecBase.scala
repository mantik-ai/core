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

import ai.mantik.executor.common.workerexec.model.{
  ListWorkerRequest,
  MnpWorkerDefinition,
  StartWorkerRequest,
  WorkerState
}
import ai.mantik.executor.model.docker.Container
import ai.mantik.testutils.TestBase

import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

trait MissingImageSpecBase {
  self: IntegrationBase with TestBase =>

  val simpleStartWorker = StartWorkerRequest(
    id = "user1",
    definition = MnpWorkerDefinition(
      container = Container(
        image = "missing_image1"
      )
    )
  )

  it should "fail correctly for missing images." in withBackend { executor =>
    // It may either fail immediately or go into error stage later
    Try {
      Await.result(executor.startWorker(simpleStartWorker), timeout)
    } match {
      case Failure(error) if error.getMessage.toLowerCase.contains("no such image") =>
        // ok (docker acts this way)
        ()
      case Failure(error) if error.getMessage.toLowerCase.contains("image error") =>
        // ok (kubernetes acts this way)
        ()
      case Failure(error) =>
        fail("Unexpected error", error)
      case Success(_) =>
        // Old behaviour of Kubernetes
        eventually {
          val listing = await(
            executor.listWorkers(
              ListWorkerRequest(
                idFilter = Some("user1")
              )
            )
          )
          listing.workers.size shouldBe 1
          listing.workers.head.state shouldBe an[WorkerState.Failed]
          listing.workers.head.state.asInstanceOf[WorkerState.Failed].error.get should include("image error")
        }
    }
  }
}

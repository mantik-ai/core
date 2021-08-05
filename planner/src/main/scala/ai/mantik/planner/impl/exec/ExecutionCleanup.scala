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
package ai.mantik.planner.impl.exec

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.Executor
import ai.mantik.executor.model.{ListWorkerRequest, ListWorkerResponse, ListWorkerResponseElement, StopWorkerRequest}
import ai.mantik.planner.repository.{MantikArtifact, Repository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

/** Helper for deleting workers of old cancelled runs */
@Singleton
class ExecutionCleanup @Inject() (executor: Executor, repo: Repository)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

  /** True if enabled */
  val isEnabled = config.getBoolean("mantik.planner.cleanupOnStart")

  /** Future which fires when the cleanup is ready. Fires immediately if disabled. */
  val isReady: Future[Unit] = {
    if (!isEnabled) {
      logger.info("Not enabled")
      Future.successful(())
    } else {
      executePruning()
    }
  }

  addShutdownHook {
    isReady
  }

  /** Execute the cleanup */
  private def executePruning(): Future[Unit] = {
    logger.info(s"Starting cleanup")

    for {
      deployedItems <- repo.list(alsoAnonymous = true, deployedOnly = true)
      workers <- executor.listWorkers(ListWorkerRequest())
      workersToDelete = calculateWorkersToDelete(workers, deployedItems)
      stoppedCount <- deleteWorkers(workersToDelete)
    } yield {
      logger.info(s"Stopped ${stoppedCount} workers")
      ()
    }
  }

  private def calculateWorkersToDelete(
      listWorkerResponse: ListWorkerResponse,
      items: IndexedSeq[MantikArtifact]
  ): Seq[ListWorkerResponseElement] = {
    val deployedWorkerNames: Set[String] = (for {
      item <- items
      deploymentInfo <- item.deploymentInfo
      name = deploymentInfo.name // contains the worker name
    } yield name).toSet

    listWorkerResponse.workers.filter { worker =>
      if (deployedWorkerNames.contains(worker.nodeName)) {
        logger.info(s"Not deleting worker ${worker.nodeName} as it is referenced by deployed Mantik Item")
        false
      } else {
        true
      }
    }
  }

  private def deleteWorkers(workers: Seq[ListWorkerResponseElement]): Future[Int] = {
    val futures = workers.map { worker =>
      logger.info(s"Stopping Worker ${worker.nodeName} in state ${worker.state}")
      executor.stopWorker(
        StopWorkerRequest(
          nameFilter = Some(worker.nodeName)
        )
      )
    }
    Future.sequence(futures).map { _.size }
  }
}

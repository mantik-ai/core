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
import ai.mantik.executor.model.{ListElement, ListElementKind, ListResponse}
import ai.mantik.planner.repository.{MantikArtifact, Repository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

/** Helper for deleting evaluations/deployments of old cancelled runs */
@Singleton
class ExecutionCleanup @Inject() (executor: Executor, repo: Repository)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase {

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
      listResponse <- executor.list()
      elementsToDelete = calculateElementsToDelete(listResponse, deployedItems)
      stoppedCount <- deleteElements(elementsToDelete)
    } yield {
      logger.info(s"Stopped ${stoppedCount} workers")
      ()
    }
  }

  private def calculateElementsToDelete(
      listResponse: ListResponse,
      items: IndexedSeq[MantikArtifact]
  ): Seq[ListElement] = {
    val deployedEvaluations: Set[String] = (for {
      item <- items
      deploymentInfo <- item.deploymentInfo
      name = deploymentInfo.evaluationId
    } yield name).toSet

    listResponse.elements.filter { element =>
      if (deployedEvaluations.contains(element.id)) {
        logger.info(s"Not deleting element ${element.id} as it is referenced by deployed Mantik Item")
        false
      } else {
        true
      }
    }
  }

  private def deleteElements(elements: Seq[ListElement]): Future[Int] = {
    val futures = elements.map { element =>
      logger.info(s"Stopping Evaluation ${element.id} in status ${element.status}")
      element.kind match {
        case ListElementKind.Evaluation => executor.cancelEvaluation(element.id)
        case ListElementKind.Deployment => executor.removeDeployment(element.id)
      }
    }
    Future.sequence(futures).map { _.size }
  }
}

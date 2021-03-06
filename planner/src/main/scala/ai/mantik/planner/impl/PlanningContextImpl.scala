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
package ai.mantik.planner.impl

import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.errors.{ErrorCodes, MantikAsyncException, MantikException}
import ai.mantik.elements.{MantikId, NamedMantikId}
import ai.mantik.planner._
import ai.mantik.planner.repository._
import akka.NotUsed
import akka.stream.scaladsl
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import java.nio.file.Path
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

private[planner] class PlanningContextImpl @Inject() (
    val planner: Planner,
    val planExecutor: PlanExecutor,
    val retriever: MantikArtifactRetriever,
    val mantikItemStateManager: MantikItemStateManager,
    fileRepo: FileRepository
)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with PlanningContext {
  private val jobTimeout = config.getFiniteDuration("mantik.planner.jobTimeout")

  override def load(id: MantikId): MantikItem = {
    val (artifact, hull) = await(retriever.get(id))
    MantikItem.fromMantikArtifact(artifact, mantikItemStateManager, hull)
  }

  override def pull(id: MantikId): MantikItem = {
    val (artifact, hull) = await(retriever.pull(id))
    MantikItem.fromMantikArtifact(artifact, mantikItemStateManager, hull)
  }

  override def execute[T](action: Action[T], meta: ActionMeta): T = {
    val plan = planner.convert(action, meta)
    val result = await(planExecutor.execute(plan), jobTimeout)
    result
  }

  private def await[T](future: Future[T], timeout: Duration = Duration.Inf) = {
    try {
      Await.result(future, timeout)
    } catch {
      case NonFatal(e) => throw new MantikAsyncException(e)
    }
  }

  override def pushLocalMantikItem(dir: Path, id: Option[NamedMantikId] = None): MantikId = {
    await(retriever.addLocalMantikItemToRepository(dir, id)).mantikId
  }

  override def state(item: MantikItem): MantikItemState = {
    mantikItemStateManager.getOrDefault(item)
  }

  override def storeFile(
      contentType: String,
      source: scaladsl.Source[ByteString, NotUsed],
      temporary: Boolean,
      contentLength: Option[Long]
  ): (String, Long) = {
    await(fileRepo.uploadNewFile(contentType, source, temporary, contentLength))
  }
}

private[mantik] object PlanningContextImpl {

  /**
    * Construct a context with components
    * (for testing)
    */
  def constructWithComponents(
      mantikItemStateManager: MantikItemStateManager,
      retriever: MantikArtifactRetriever,
      planExecutor: PlanExecutor,
      fileRepo: FileRepository
  )(implicit akkaRuntime: AkkaRuntime): PlanningContextImpl = {

    val planner = new PlannerImpl(akkaRuntime.config, mantikItemStateManager)

    val context =
      new PlanningContextImpl(
        planner,
        planExecutor,
        retriever,
        mantikItemStateManager,
        fileRepo
      )
    context
  }
}

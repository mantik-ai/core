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
import ai.mantik.elements.errors.MantikAsyncException
import ai.mantik.elements.{MantikId, NamedMantikId}
import ai.mantik.executor.Executor
import ai.mantik.planner._
import ai.mantik.planner.impl.exec.{
  ExecutionCleanup,
  ExecutionPayloadProvider,
  MnpPlanExecutor,
  MnpWorkerManager,
  UiStateService
}
import ai.mantik.planner.repository.impl.{LocalMantikRegistryImpl, MantikArtifactRetrieverImpl}
import ai.mantik.planner.repository._

import java.nio.file.Path
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

private[planner] class PlanningContextImpl @Inject() (
    val localRegistry: LocalMantikRegistry,
    val planner: Planner,
    val planExecutor: PlanExecutor,
    val remoteRegistry: RemoteMantikRegistry,
    val retriever: MantikArtifactRetriever,
    val mantikItemStateManager: MantikItemStateManager,
    val metrics: Metrics,
    val executor: Executor
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
}

private[mantik] object PlanningContextImpl {

  /**
    * Construct a context with components
    * (for testing)
    */
  def constructWithComponents(
      repository: Repository,
      fileRepository: FileRepository,
      // fileRepositoryServer: FileRepositoryServer,
      executor: Executor,
      registry: RemoteMantikRegistry,
      payloadProvider: ExecutionPayloadProvider
  )(implicit akkaRuntime: AkkaRuntime): PlanningContextImpl = {
    val metrics = new Metrics()
    val mantikItemStateManager = new MantikItemStateManager()
    val planner = new PlannerImpl(akkaRuntime.config, mantikItemStateManager)
    val localRegistry = new LocalMantikRegistryImpl(fileRepository, repository)
    val retriever = new MantikArtifactRetrieverImpl(localRegistry, registry)
    val uiStateService = new UiStateService(executor, metrics)
    // val fileRepositoryServerRemotePresence = new FileRepositoryServerRemotePresence(fileRepositoryServer, executor)
    val executionCleanup = new ExecutionCleanup(executor, repository)
    val mnpWorkerManager = new MnpWorkerManager(
      executor,
      metrics
    )
    val planExecutor = new MnpPlanExecutor(
      fileRepository,
      repository,
      retriever,
      payloadProvider,
      mantikItemStateManager,
      uiStateService,
      executionCleanup,
      mnpWorkerManager,
      metrics
    )
    val context =
      new PlanningContextImpl(
        localRegistry,
        planner,
        planExecutor,
        registry,
        retriever,
        mantikItemStateManager,
        metrics,
        executor
      )
    context
  }
}

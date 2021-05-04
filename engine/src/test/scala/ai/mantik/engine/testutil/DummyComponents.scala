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
package ai.mantik.engine.testutil

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.planner.impl.{MantikItemStateManager, PlannerImpl}
import ai.mantik.planner.repository.impl.{
  LocalMantikRegistryImpl,
  MantikArtifactRetrieverImpl,
  TempFileRepository,
  TempRepository
}
import ai.mantik.planner.repository.{MantikArtifactRetriever, RemoteMantikRegistry, Repository}
import ai.mantik.planner.{CoreComponents, Plan, PlanExecutor, Planner}
import org.apache.commons.io.FileUtils

import scala.concurrent.Future

class DummyComponents(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with CoreComponents {

  lazy val fileRepository = new TempFileRepository()
  lazy val repository: Repository = new TempRepository()
  override lazy val localRegistry = new LocalMantikRegistryImpl(fileRepository, repository)
  private lazy val registry: RemoteMantikRegistry = RemoteMantikRegistry.empty

  override def retriever: MantikArtifactRetriever = new MantikArtifactRetrieverImpl(
    localRegistry,
    registry
  )

  val stateManger = new MantikItemStateManager()
  override lazy val planner: Planner = new PlannerImpl(config, stateManger)

  var nextItemToReturnByExecutor: Future[_] = Future.failed(
    new RuntimeException("Plan executor not implemented")
  )
  var lastPlan: Plan[_] = null

  override lazy val planExecutor: PlanExecutor = {
    new PlanExecutor {
      override def execute[T](plan: Plan[T]): Future[T] = {
        lastPlan = plan
        nextItemToReturnByExecutor.asInstanceOf[Future[T]]
      }
    }
  }

  addShutdownHook {
    FileUtils.deleteDirectory(fileRepository.directory.toFile)
    Future.successful(())
  }

  /** Create a shared copy, which doesn't shut down on shutdown() */
  def shared(): CoreComponents = this
  // TODO: Remove me
}

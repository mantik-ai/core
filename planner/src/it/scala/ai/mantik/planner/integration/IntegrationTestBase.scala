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
package ai.mantik.planner.integration

import ai.mantik.componently.MetricRegistry
import ai.mantik.componently.utils.GlobalLocalAkkaRuntime
import ai.mantik.elements.errors.ConfigurationException
import ai.mantik.executor.ExecutorForIntegrationTest
import ai.mantik.executor.docker.DockerExecutorForIntegrationTest
import ai.mantik.executor.kubernetes.KubernetesExecutorForIntegrationTests
import ai.mantik.planner.PlanningContext
import ai.mantik.planner.impl.exec.{ExecutionCleanup, PlanExecutorImpl, UiStateService}
import ai.mantik.planner.impl.{MantikItemStateManager, PlanningContextImpl}
import ai.mantik.planner.repository.impl.{
  LocalMantikRegistryImpl,
  MantikArtifactRetrieverImpl,
  TempFileRepository,
  TempRepository
}
import ai.mantik.planner.repository.{FileRepository, MantikArtifactRetriever, RemoteMantikRegistry}
import ai.mantik.testutils.{AkkaSupport, TestBase}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.time.{Millis, Span}

import scala.concurrent.duration._

/** Base class for integration tests having a full running executor instance. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport with GlobalLocalAkkaRuntime {

  protected var embeddedExecutor: ExecutorForIntegrationTest = _
  override protected lazy val typesafeConfig: Config = ConfigFactory.load("systemtest.conf")
  private var _context: PlanningContextImpl = _
  private var _fileRepo: FileRepository = _

  implicit def context: PlanningContext = _context

  protected def fileRepository: FileRepository = _fileRepo

  protected def retriever: MantikArtifactRetriever = _context.retriever

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    embeddedExecutor = makeExecutorForIntegrationTest()
    embeddedExecutor.scrap()
    embeddedExecutor.start()

    val repository = new TempRepository()
    _fileRepo = new TempFileRepository()
    val registry = RemoteMantikRegistry.empty
    val localRegistry = new LocalMantikRegistryImpl(fileRepository, repository)
    val retriever = new MantikArtifactRetrieverImpl(localRegistry, registry)
    val mantikItemStateManager = new MantikItemStateManager()
    val metrics = new MetricRegistry()
    val uiStateService = new UiStateService(embeddedExecutor.executor, metrics)
    val executionCleanup = new ExecutionCleanup(embeddedExecutor.executor, repository)

    val planExecutor = new PlanExecutorImpl(
      _fileRepo,
      repository,
      retriever,
      mantikItemStateManager,
      uiStateService,
      embeddedExecutor.executor,
      executionCleanup
    )

    _context = PlanningContextImpl.constructWithComponents(
      mantikItemStateManager,
      retriever,
      planExecutor,
      _fileRepo
    )
  }

  private def makeExecutorForIntegrationTest(): ExecutorForIntegrationTest = {
    val executorType = typesafeConfig.getString("mantik.executor.type")
    executorType match {
      case "docker"     => return new DockerExecutorForIntegrationTest(typesafeConfig)
      case "kubernetes" => return new KubernetesExecutorForIntegrationTests(typesafeConfig)
      case _            => throw new ConfigurationException("Bad executor type")
    }
  }

  override protected def afterAll(): Unit = {
    embeddedExecutor.stop()
    super.afterAll()
  }
}

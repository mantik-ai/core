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

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.PayloadProvider
import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.common.test.integration.IntegrationBase
import ai.mantik.executor.common.workerexec.{WorkerExecutorBackend, LocalServerPayloadProvider}
import ai.mantik.executor.docker.api.DockerClient
import ai.mantik.executor.docker.api.structures.ListNetworkRequestFilter
import ai.mantik.executor.docker.{DockerConstants, DockerWorkerExecutorBackend, DockerExecutorConfig}
import ai.mantik.testutils.{AkkaSupport, TempDirSupport, TestBase}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.time.{Millis, Span}

import scala.concurrent.duration._

abstract class IntegrationTestBase extends TestBase with AkkaSupport with TempDirSupport with IntegrationBase {
  implicit def akkaRuntime: AkkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)

  protected lazy val dockerClient: DockerClient = new DockerClient()

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    killOldMantikContainers()
  }

  private def killOldMantikContainers(): Unit = {
    val containers = await(dockerClient.listContainers((true)))
    val mantikContainers = containers.filter(
      _.Labels.get(LabelConstants.ManagedByLabelName).contains(LabelConstants.ManagedByLabelValue)
    )
    if (mantikContainers.isEmpty) {
      logger.info("No old mantik containers to kill")
    }
    mantikContainers.foreach { container =>
      logger.info(s"Killing Container ${container.Names}/${container.Id}")
      await(dockerClient.removeContainer(container.Id, true))
    }
    val volumes = await(dockerClient.listVolumes(()))
    val mantikVolumes = volumes.Volumes.filter(
      _.effectiveLabels.get(LabelConstants.ManagedByLabelValue).contains(LabelConstants.ManagedByLabelName)
    )
    if (mantikVolumes.isEmpty) {
      logger.info("No old mantik volumes to kill")
    }
    mantikVolumes.foreach { volume =>
      logger.info(s"Killing Volume ${volume.Name}")
      await(dockerClient.removeVolume(volume.Name))
    }

    val mantikNetworks = await(
      dockerClient.listNetworksFiltered(
        ListNetworkRequestFilter.forLabels(LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue)
      )
    )
    mantikNetworks.foreach { network =>
      logger.info(s"Killing network ${network.Name}")
      await(dockerClient.removeNetwork(network.Id))
    }
  }

  override protected lazy val typesafeConfig: Config = {
    ConfigFactory.load("systemtest.conf")
  }

  override def withBackend[T](f: WorkerExecutorBackend => T): T = {
    val config = DockerExecutorConfig.fromTypesafeConfig(typesafeConfig)
    val extraLifecycle = akkaRuntime.withExtraLifecycle()
    val executor = new DockerWorkerExecutorBackend(dockerClient, config)(extraLifecycle)
    try {
      f(executor)
    } finally {
      extraLifecycle.shutdown()
    }
  }

  override def withPayloadProvider[T](f: PayloadProvider => T): T = {
    val extraLifecycle = akkaRuntime.withExtraLifecycle()
    val provider = new LocalServerPayloadProvider()(extraLifecycle)
    try {
      f(provider)
    } finally {
      extraLifecycle.shutdown()
    }
  }
}

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
package ai.mantik.executor.docker

import ai.mantik.componently.{AkkaRuntime, MetricRegistry}
import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.common.workerexec.{LocalServerPayloadProvider, WorkerBasedExecutor, WorkerMetrics}
import ai.mantik.executor.docker.api.DockerClient
import ai.mantik.executor.docker.api.structures.ListContainerRequestFilter
import ai.mantik.executor.{ExecutorForIntegrationTest, Executor}
import com.typesafe.config.{Config => TypesafeConfig}
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DockerExecutorForIntegrationTest(config: TypesafeConfig)(implicit akkaRuntime: AkkaRuntime)
    extends ExecutorForIntegrationTest {
  val logger = Logger(getClass)

  val executorConfig = DockerExecutorConfig.fromTypesafeConfig(config)
  val dockerClient = new DockerClient()
  var _backend: Option[DockerWorkerExecutorBackend] = None

  private var _scopedAkkaRuntime: AkkaRuntime = _
  private var _payloadProvider: LocalServerPayloadProvider = _
  private var _executor: Option[WorkerBasedExecutor] = None

  override def executor: Executor = _executor.getOrElse(
    throw new IllegalStateException(s"Not yet started")
  )

  override def start(): Unit = {
    _scopedAkkaRuntime = akkaRuntime.withExtraLifecycle()
    _backend = Some(new DockerWorkerExecutorBackend(dockerClient, executorConfig)(_scopedAkkaRuntime))
    _payloadProvider = new LocalServerPayloadProvider()(_scopedAkkaRuntime)
    _executor = Some(
      new WorkerBasedExecutor(
        _backend.get,
        new WorkerMetrics(new MetricRegistry),
        _payloadProvider
      )
    )
  }

  /** Remove old containers */
  def scrap(): Unit = {
    def await[T](f: Future[T]): T = {
      Await.result(f, 60.seconds)
    }
    val mantikContainers = await(
      dockerClient.listContainersFiltered(
        true,
        ListContainerRequestFilter.forLabelKeyValue(
          LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
        )
      )
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
  }

  def stop(): Unit = {
    _scopedAkkaRuntime.shutdown()
    _scopedAkkaRuntime = null
  }
}

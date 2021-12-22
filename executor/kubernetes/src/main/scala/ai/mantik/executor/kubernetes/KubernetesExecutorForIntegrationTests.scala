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
package ai.mantik.executor.kubernetes

import java.time.Clock
import ai.mantik.componently.{AkkaRuntime, MetricRegistry}
import ai.mantik.executor.common.workerexec.{ExecutorFileStoragePayloadProvider, WorkerBasedExecutor, WorkerMetrics}
import ai.mantik.executor.s3storage.S3Storage
import ai.mantik.executor.{ExecutorForIntegrationTest, Executor}
import com.typesafe.config.{Config => TypesafeConfig}

/** An embedded executor for integration tests. */
class KubernetesExecutorForIntegrationTests(config: TypesafeConfig)(implicit akkaRuntime: AkkaRuntime)
    extends ExecutorForIntegrationTest {

  val executorConfig = Config.fromTypesafeConfig(config)
  implicit val clock = Clock.systemUTC()
  import ai.mantik.componently.AkkaHelper._
  val kubernetesClient = skuber.k8sInit
  private var _executorBackend: Option[KubernetesWorkerExecutorBackend] = None
  private var _scopedAkkaRuntime: AkkaRuntime = null
  private var _executor: Option[WorkerBasedExecutor] = None

  override def executor: Executor = _executor.getOrElse {
    throw new IllegalStateException(s"Executor not yet started")
  }

  override def start(): Unit = {
    _scopedAkkaRuntime = akkaRuntime.withExtraLifecycle()
    val k8sOperations = new K8sOperations(executorConfig, kubernetesClient)(_scopedAkkaRuntime)
    val backend = new KubernetesWorkerExecutorBackend(executorConfig, k8sOperations)(_scopedAkkaRuntime)
    val s3 = new S3Storage()(_scopedAkkaRuntime)
    val payloadProvider = new ExecutorFileStoragePayloadProvider(s3)(_scopedAkkaRuntime)
    val metricRegistry = new MetricRegistry()
    val workerMetrics = new WorkerMetrics(metricRegistry)
    val executor = new WorkerBasedExecutor(backend, workerMetrics, payloadProvider)
    _executor = Some(executor)

    _executorBackend = Some(backend)
  }

  def stop(): Unit = {
    kubernetesClient.close
    _scopedAkkaRuntime.shutdown()
  }

  override def scrap(): Unit = {
    if (_executorBackend.nonEmpty) {
      throw new IllegalStateException(s"Executor already started")
    }
    val cleaner = new KubernetesCleaner(kubernetesClient, executorConfig)
    cleaner.deleteKubernetesContent()
  }
}

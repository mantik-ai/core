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
package ai.mantik.executor.kubernetes.integration

import java.time.Clock
import ai.mantik.executor.PayloadProvider
import ai.mantik.executor.common.test.integration.IntegrationBase
import ai.mantik.executor.common.workerexec.{WorkerExecutorBackend, LocalServerPayloadProvider}
import ai.mantik.executor.kubernetes.{K8sOperations, KubernetesWorkerExecutorBackend}

import scala.annotation.nowarn
import scala.concurrent.duration.{FiniteDuration, _}

abstract class IntegrationTestBase extends KubernetesTestBase with IntegrationBase {

  private var _executor: KubernetesWorkerExecutorBackend = _

  override protected val timeout: FiniteDuration = 60.seconds

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    implicit val clock = Clock.systemUTC()
    val k8sOperations = new K8sOperations(config, _kubernetesClient)
    _executor = new KubernetesWorkerExecutorBackend(config, k8sOperations)
  }

  @nowarn
  protected trait Env extends super.Env {
    val executor: WorkerExecutorBackend = _executor
  }

  override def withBackend[T](f: WorkerExecutorBackend => T): T = {
    val env = new Env {}
    f(env.executor)
  }

  override def withPayloadProvider[T](f: PayloadProvider => T): T = {
    // Note: this is not really correct in Case of Kubernetes, however the S3 Payload Provider
    // is not available in this namespace, and the Integration Tests do not use payload in the moment
    val extraLifecycle = akkaRuntime.withExtraLifecycle()
    val provider = new LocalServerPayloadProvider()(extraLifecycle)
    try {
      f(provider)
    } finally {
      extraLifecycle.shutdown()
    }
  }
}

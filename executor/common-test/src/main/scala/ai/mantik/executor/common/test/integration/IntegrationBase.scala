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
package ai.mantik.executor.common.test.integration

import ai.mantik.componently.{AkkaRuntime, MetricRegistry}
import ai.mantik.executor.common.workerexec.{WorkerExecutorBackend, WorkerBasedExecutor, WorkerMetrics}
import ai.mantik.executor.{Executor, PayloadProvider}
import ai.mantik.testutils.{AkkaSupport, TestBase}

trait IntegrationBase {
  self: TestBase =>

  implicit protected def akkaRuntime: AkkaRuntime

  def withBackend[T](f: WorkerExecutorBackend => T): T

  def withExecutor[T](f: Executor => T): T = {
    withBackend { executor =>
      withPayloadProvider { payloadProvider =>
        val workerMetrics = new WorkerMetrics(new MetricRegistry)
        val workerBasedExecutor = new WorkerBasedExecutor(executor, workerMetrics, payloadProvider)(akkaRuntime)
        f(workerBasedExecutor)
      }
    }
  }

  def withPayloadProvider[T](f: PayloadProvider => T): T
}

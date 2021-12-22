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
package ai.mantik.executor.common.workerexec

import ai.mantik.componently.MetricRegistry
import com.codahale.metrics.Counter

import javax.inject.{Inject, Singleton}

/** Contains Metrics for workers */
@Singleton
class WorkerMetrics @Inject() (registry: MetricRegistry) {

  private def reg = registry.registry

  /** Prefix for Planner Metrics */
  val prefix = "mantik.executor.worker."

  /** Count the number of active workers. */
  def workers: Counter = reg.counter(prefix + "workers")

  /** Count the number of created workers (regular). */
  def workersCreated: Counter = reg.counter(prefix + "workers-created")

  /** Count the number of open mnp connections. */
  def mnpConnections: Counter = reg.counter(prefix + "mnp-connections")

  /** Count the number of mnp connections created. */
  def mnpConnectionsCreated: Counter = reg.counter(prefix + "mnp-connections-created")

  /** The number of bytes pushed to MNP Nodes */
  val mnpPushBytes: Counter = reg.counter(prefix + "mnp-push-bytes")

  /** The number of bytes pulled from MNP Nodes */
  val mnpPullBytes: Counter = reg.counter(prefix + "mnp-pull-bytes")

  /** Count the number of permanent workers. */
  val permanentWorkersCreated = reg.counter(prefix + "permanent-workers-created")

  /** Count the number of permanent pipelines. */
  val permanentPipelinesCreated = reg.counter(prefix + "permanent-pipelines-created")
}

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

import com.codahale.metrics.{Metric, MetricRegistry}

import javax.inject.Singleton

/** Provides access to Metrics for the planner */
@Singleton
private[planner] class Metrics {
  private val metricsRegistry = new MetricRegistry()

  /** Prefix for Planner Metrics */
  val prefix = "mantik.planner."

  /** Return all metrics. */
  def all: Map[String, Metric] = {
    import scala.collection.JavaConverters._
    metricsRegistry.getMetrics.asScala.toMap
  }

  /** Count the number of open mnp connections. */
  val mnpConnections = metricsRegistry.counter(prefix + "mnp-connections")

  /** Count the number of mnp connections created. */
  val mnpConnectionsCreated = metricsRegistry.counter(prefix + "mnp-connections-created")

  /** Count the number of created workers (regular). */
  val workersCreated = metricsRegistry.counter(prefix + "workers-created")

  /** Count the number of active workers. */
  val workers = metricsRegistry.counter(prefix + "workers")

  /** Count the number of permanent workers. */
  val permanentWorkersCreated = metricsRegistry.counter(prefix + "permanent-workers-created")

  /** Count the number of permanent pipelines. */
  val permanentPipelinesCreated = metricsRegistry.counter(prefix + "permanent-pipelines-created")

  /** The number of bytes pushed to MNP Nodes */
  val mnpPushBytes = metricsRegistry.counter(prefix + "mnp-push-bytes")

  /** The number of bytes pulled from MNP Nodes */
  val mnpPullBytes = metricsRegistry.counter(prefix + "mnp-pull-bytes")
}

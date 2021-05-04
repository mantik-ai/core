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
package ai.mantik.executor.common

/** Constants for Labels on Containers/Kubernetes Items. */
object LabelConstants {

  /** Name of the label containing the (internal) job id. */
  val InternalIdLabelName = "ai.mantik.internalId"

  /** Name of a user provided id label. */
  val UserIdLabelName = "ai.mantik.userId"

  /** Label for manged by */
  val ManagedByLabelName = "app.kubernetes.io/managed-by"
  val ManagedByLabelValue = "mantik"

  /** Name of the label defining the role */
  val RoleLabelName = "ai.mantik.role"

  object role {

    /** Regular Worker */
    val worker = "worker"

    /** The Ingress Traefik Resource */
    val traefik = "traefik"

    /** Grpc Proxy Role */
    val grpcProxy = "grpcproxy"
  }

  /** Which kind of Worker */
  val WorkerTypeLabelName = "ai.mantik.worker.type"

  object workerType {

    /** An MNP Worker */
    val mnpWorker = "mnp-worker"

    /** An MNP Pipeline */
    val mnpPipeline = "mnp-pipeline"
  }
}

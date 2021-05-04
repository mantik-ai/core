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
package ai.mantik.planner

import ai.mantik.planner.repository.{LocalMantikRegistry, MantikArtifactRetriever}
import com.google.inject.ImplementedBy
import javax.inject.Inject

/** Encapsulates access to the core components of Mantik. */
@ImplementedBy(classOf[CoreComponents.CoreComponentsImpl])
trait CoreComponents {

  /** The local mantik registry. */
  def localRegistry: LocalMantikRegistry

  /** Access to the Artifact Retriever */
  def retriever: MantikArtifactRetriever

  /** Access to the planner. */
  def planner: Planner

  /** Access to the plan executor. */
  def planExecutor: PlanExecutor
}

object CoreComponents {
  class CoreComponentsImpl @Inject() (
      val localRegistry: LocalMantikRegistry,
      val retriever: MantikArtifactRetriever,
      val planner: Planner,
      val planExecutor: PlanExecutor
  ) extends CoreComponents
}

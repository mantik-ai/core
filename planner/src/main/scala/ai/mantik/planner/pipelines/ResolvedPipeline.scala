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
package ai.mantik.planner.pipelines

import ai.mantik.ds.functional.FunctionType
import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec
import ai.mantik.ds.sql.Select
import ai.mantik.elements.PipelineStep
import ai.mantik.planner.Algorithm
import ai.mantik.planner.impl.MantikItemCodec
import io.circe.{Decoder, ObjectEncoder}

/**
  * A Resolved pipeline.
  * All pipeline steps are matched to algorithms.
  */
private[planner] case class ResolvedPipeline(
    steps: List[ResolvedPipelineStep],
    functionType: FunctionType
) {

  /** Build a Map of Referenced Algorithms. */
  private[planner] def referencedAlgorithms: PipelineResolver.ReferencedAlgorithms = {
    steps.collect { case as: ResolvedPipelineStep.AlgorithmStep =>
      as.algorithm.mantikId -> as.algorithm
    }.toMap
  }
}

private[planner] sealed trait ResolvedPipelineStep {

  /** The function type of this pipeline step. */
  def functionType: FunctionType
}

private[planner] object ResolvedPipelineStep {

  case class AlgorithmStep(algorithm: Algorithm) extends ResolvedPipelineStep {
    override def functionType: FunctionType = algorithm.functionType
  }

  case class SelectStep(select: Select) extends ResolvedPipelineStep {
    override def functionType: FunctionType = FunctionType(select.inputTabularType, select.resultingTabularType)
  }
}

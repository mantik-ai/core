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

import ai.mantik.ds.DataType
import ai.mantik.ds.sql.Select
import ai.mantik.elements
import ai.mantik.elements.{
  MantikHeader,
  MantikId,
  NamedMantikId,
  OptionalFunctionType,
  PipelineDefinition,
  PipelineStep
}
import ai.mantik.planner.Pipeline.PipelineBuildStep
import ai.mantik.planner.{Algorithm, DefinitionSource, MantikItem, Pipeline, Source}

private[planner] object PipelineBuilder {

  /** Build a pipeline from a mantikHeader and algorithms. */
  def buildOrFailFromMantikHeader(
      source: DefinitionSource,
      mantikHeader: MantikHeader[PipelineDefinition],
      referenced: Map[MantikId, MantikItem]
  ): Pipeline = {
    val resolved = PipelineResolver.resolvePipeline(
      mantikHeader,
      referenced
    ) match {
      case Left(error) => throw error
      case Right(ok)   => ok
    }
    new Pipeline(
      source,
      mantikHeader,
      resolved
    )
  }

  /** Build a Pipeline from algorithms writing an artifical mantik header. */
  def build(steps: Seq[Either[Select, Algorithm]]): Either[PipelineException, Pipeline] = {
    val inputType = steps.headOption.map {
      case Left(select)     => select.inputTabularType
      case Right(algorithm) => algorithm.functionType.input
    }
    val highLevelSteps = steps.map {
      case Left(select)     => PipelineBuildStep.SelectBuildStep(select.toStatement)
      case Right(algorithm) => PipelineBuildStep.AlgorithmBuildStep(algorithm)
    }
    build(highLevelSteps, inputType)
  }

  /**
    * Build a pipeline from high level steps.
    * @param highLevelSteps the parts of the pipeline.
    * @param definedInputType a defined input type, necessary if the first step is a SELECT.
    */
  def build(
      highLevelSteps: Seq[PipelineBuildStep],
      definedInputType: Option[DataType] = None
  ): Either[PipelineException, Pipeline] = {
    if (highLevelSteps.isEmpty) {
      return Left(new InvalidPipelineException("Empty pipeline"))
    }

    val inputType: Option[DataType] = definedInputType.orElse {
      highLevelSteps.headOption.flatMap {
        case PipelineBuildStep.AlgorithmBuildStep(algorithm) => Some(algorithm.functionType.input)
        case _                                               => None
      }
    }

    val steps: Seq[(PipelineStep, Option[Algorithm])] = highLevelSteps.map {
      case PipelineBuildStep.AlgorithmBuildStep(algorithm) =>
        val id = algorithm.mantikId
        PipelineStep.AlgorithmStep(algorithm = id) -> Some(algorithm)
      case PipelineBuildStep.SelectBuildStep(statement) =>
        // A select statement
        PipelineStep.SelectStep(statement) -> None
    }

    val pipelineDefinition = elements.PipelineDefinition(
      steps = steps.map(_._1).toList,
      `type` = Some(
        OptionalFunctionType(
          input = inputType
        )
      )
    )

    val referenced = steps.collect { case (as: PipelineStep.AlgorithmStep, Some(algorithm)) =>
      as.algorithm -> algorithm
    }.toMap

    val mantikHeader = MantikHeader.pure(pipelineDefinition)
    PipelineResolver
      .resolvePipeline(
        mantikHeader,
        referenced
      )
      .map { resolved =>
        new Pipeline(DefinitionSource.Constructed(), mantikHeader, resolved)
      }
  }
}

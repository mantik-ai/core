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

import ai.mantik.ds.formats.json.JsonFormat
import ai.mantik.ds.functional.FunctionType
import ai.mantik.ds.sql.Select
import ai.mantik.ds.{DataType, TabularData}
import ai.mantik.elements.PipelineStep.MetaVariableSetting
import ai.mantik.elements.{MantikHeader, MantikId, NamedMantikId, PipelineDefinition, PipelineStep}
import ai.mantik.planner.{Algorithm, MantikItem}
import ai.mantik.elements.meta.MetaVariableException

/** Resolves pipelines, figures out types and applies Meta Variables. */
private[planner] object PipelineResolver {

  /** Map of preloaded Algorithms. */
  type ReferencedAlgorithms = Map[MantikId, MantikItem]

  /**
    * Resolves a pipeline.
    *
    * Meta Variables are applied to the sub algorithms and the final type is checked.
    *
    * @param mantikHeader the pipeline mantik header
    * @param algorithms the algorithm mantik headers.
    *
    * @return either an error or the resolved pipeline.
    */
  def resolvePipeline(
      mantikHeader: MantikHeader[PipelineDefinition],
      algorithms: ReferencedAlgorithms
  ): Either[PipelineException, ResolvedPipeline] = {
    try {
      val inputType = figureOutInputType(mantikHeader, algorithms)
      val (steps, outputType) = foldingMap(mantikHeader.definition.steps.zipWithIndex, inputType) {
        case ((step, stepNum), currentType) =>
          val resolvedPipelineStep = resolvePipelineStep(stepNum, currentType, step, algorithms)
          resolvedPipelineStep -> resolvedPipelineStep.functionType.output
      }
      mantikHeader.definition.outputType.foreach { expectedOutputType =>
        if (expectedOutputType != outputType) {
          throw new PipelineTypeException(
            s"Expected output type ${expectedOutputType} doesn't match calculated output type ${outputType}"
          )
        }
      }
      Right(
        ResolvedPipeline(
          steps,
          FunctionType(
            inputType,
            outputType
          )
        )
      )
    } catch {
      case e: PipelineException => Left(e)
      case o: Exception         => Left(new PipelineException(o.getMessage, o))
    }
  }

  /**
    * Like fold and map in one step. Applies a transformation to all values in list by also updating a rolling state.
    * @tparam A input type
    * @tparam B result type
    * @tparam S rolling state type
    * @param in input values
    * @param initial initial state
    * @param f function transforming an input value and the current state into a result value with new state.
    */
  private def foldingMap[A, B, S](in: List[A], initial: S)(f: (A, S) => (B, S)): (List[B], S) = {
    // TODO: Move to generic package #35
    val builder = List.newBuilder[B]
    var s = initial
    in.foreach { value =>
      val (transformed, newState) = f(value, s)
      s = newState
      builder += transformed
    }
    (builder.result(), s)
  }

  private def figureOutInputType(
      mantikHeader: MantikHeader[PipelineDefinition],
      algorithms: ReferencedAlgorithms
  ): DataType = {
    mantikHeader.definition.inputType.getOrElse {
      // try to figure out type
      mantikHeader.definition.steps.headOption match {
        case None => throw new InvalidPipelineException("Empty Pipeline")
        case Some(as: PipelineStep.AlgorithmStep) =>
          resolveAlgorithmPipelineStep(0, None, as, algorithms).functionType.input
        case Some(other) =>
          throw new InvalidPipelineException(
            s"Cannot deduct input type of pipeline, either describe it or let it start with algorithm, got ${other}"
          )
      }
    }
  }

  /**
    * Resolve a single pipe line step.
    * @throws PipelineException
    */
  private def resolvePipelineStep(
      stepNum: Int,
      inputDataType: DataType,
      pipelineStep: PipelineStep,
      algorithms: ReferencedAlgorithms
  ): ResolvedPipelineStep = {
    pipelineStep match {
      case as: PipelineStep.AlgorithmStep =>
        resolveAlgorithmPipelineStep(stepNum, Some(inputDataType), as, algorithms)
      case s: PipelineStep.SelectStep =>
        resolveSelectStep(stepNum, inputDataType, s)
    }
  }

  private def resolveAlgorithmPipelineStep(
      stepNum: Int,
      incomingDataType: Option[DataType],
      as: PipelineStep.AlgorithmStep,
      algorithms: ReferencedAlgorithms
  ): ResolvedPipelineStep.AlgorithmStep = {
    algorithms.get(as.algorithm) match {
      case None => throw new InvalidPipelineException(s"Missing element ${as.algorithm}")
      case Some(algorithm: Algorithm) =>
        val algorithmWithMetaVariables =
          try {
            applyMetaVariables(algorithm, as.metaVariables.getOrElse(Nil))
          } catch {
            case e: MetaVariableException =>
              throw new InvalidPipelineException(
                s"Could not apply meta variables to step ${stepNum}/${as.algorithm}",
                e
              )
          }
        incomingDataType.foreach { inputDataType =>
          if (algorithmWithMetaVariables.functionType.input != inputDataType) {
            throw new PipelineTypeException(
              s"Type mismatch on step${stepNum}, input ${inputDataType} expected ${algorithmWithMetaVariables.functionType.input}"
            )
          }
        }
        ResolvedPipelineStep.AlgorithmStep(
          algorithmWithMetaVariables
        )
      case Some(other) =>
        throw new InvalidPipelineException(
          s"${as.algorithm} references no algorithm but an ${other.getClass.getSimpleName}"
        )
    }
  }

  private def resolveSelectStep(
      stepNum: Int,
      inputDataType: DataType,
      s: PipelineStep.SelectStep
  ): ResolvedPipelineStep.SelectStep = {
    val select = inputDataType match {
      case t: TabularData =>
        Select.parse(t, s.select) match {
          case Left(msg) => throw new InvalidPipelineException(s"Could not parse select: ${msg}")
          case Right(ok) => ok
        }
      case other =>
        throw new PipelineTypeException(s"Only tabular data types can be transformed via selects, got ${other}")
    }
    ResolvedPipelineStep.SelectStep(select)
  }

  /**
    * Apply given meta variables to an algorithm.
    * @throws MetaVariableException on error
    */
  private def applyMetaVariables(algorithm: Algorithm, metaVariables: List[MetaVariableSetting]): Algorithm = {
    if (metaVariables.isEmpty) {
      return algorithm
    }
    val newValues = metaVariables.map { ms: MetaVariableSetting =>
      val mv = algorithm.mantikHeader.metaJson.metaVariable(ms.name).getOrElse {
        throw new InvalidPipelineException(s"Meta variable ${ms.name} not found in algorithm ${algorithm}")
      }
      val newValue = JsonFormat.deserializeBundleValue(mv.value.model, ms.value) match {
        case Left(error) =>
          throw new MetaVariableException(
            s"Meta variable ${ms.name} cannot be applied to ${ms.value}, invalid type (expected ${mv.value.model})"
          )
        case Right(ok) => ok.toSingleElementBundle
      }
      mv.name -> newValue
    }
    algorithm.withMetaValues(newValues: _*)
  }

}

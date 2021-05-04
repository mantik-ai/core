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

import ai.mantik.ds.DataType
import ai.mantik.ds.element.SingleElementBundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ItemId, MantikHeader, TrainableAlgorithmDefinition}
import ai.mantik.planner.repository.Bridge

/**
  * A trainable algorithm
  */
final case class TrainableAlgorithm(
    core: MantikItemCore[TrainableAlgorithmDefinition],
    trainedBridge: Bridge
) extends BridgedMantikItem {

  override type DefinitionType = TrainableAlgorithmDefinition
  override type OwnType = TrainableAlgorithm

  def trainingDataType: DataType = mantikHeader.definition.trainingType

  def statType: DataType = mantikHeader.definition.statType

  def functionType: FunctionType = mantikHeader.definition.`type`

  /**
    * Train this [[TrainableAlgorithm]] with given trainingData.
    * @param cached if true, the result will be cached. This is important for if you want to access the training result and stats.
    */
  def train(trainingData: DataSet, cached: Boolean = true): (Algorithm, DataSet) = {
    val adapted = trainingData.autoAdaptOrFail(trainingDataType)

    val op = Operation.Training(this, adapted)

    val trainedMantikHeader = MantikHeader.generateTrainedMantikHeader(mantikHeader) match {
      case Left(err) => throw new RuntimeException(s"Could not generate trained mantikfle", err)
      case Right(ok) => ok
    }

    val opResult = PayloadSource.OperationResult(op)

    val algorithmId = ItemId.generate()
    val statsId = ItemId.generate()

    val result = if (cached) {
      PayloadSource.Cached(opResult, Vector(algorithmId, statsId))
    } else {
      opResult
    }

    val algorithmResult = Algorithm(
      Source.constructed(PayloadSource.Projection(result)),
      trainedMantikHeader,
      trainedBridge
    ).withItemId(algorithmId)

    val statsResult = DataSet
      .natural(Source.constructed(PayloadSource.Projection(result, 1)), statType)
      .withItemId(
        statsId
      )
    (algorithmResult, statsResult)
  }

  override protected def withCore(core: MantikItemCore[TrainableAlgorithmDefinition]): TrainableAlgorithm = {
    copy(core = core)
  }
}

object TrainableAlgorithm {

  def apply(
      source: Source,
      mantikHeader: MantikHeader[TrainableAlgorithmDefinition],
      bridge: Bridge,
      trainedBridge: Bridge
  ): TrainableAlgorithm = {
    TrainableAlgorithm(MantikItemCore(source, mantikHeader, bridge), trainedBridge)
  }
}

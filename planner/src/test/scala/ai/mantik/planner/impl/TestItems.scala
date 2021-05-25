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

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.functional.FunctionType
import ai.mantik.elements
import ai.mantik.elements.{AlgorithmDefinition, DataSetDefinition, MantikHeader, TrainableAlgorithmDefinition}
import ai.mantik.planner.repository.Bridge
import ai.mantik.planner.util.FakeBridges

object TestItems extends FakeBridges {

  val algorithm1 = MantikHeader.pure(
    AlgorithmDefinition(
      bridge = algoBridge.mantikId,
      `type` = FunctionType(
        input = FundamentalType.Int32,
        output = FundamentalType.StringType
      )
    )
  )

  val algorithm2 = MantikHeader.pure(
    AlgorithmDefinition(
      bridge = algoBridge.mantikId,
      `type` = FunctionType(
        input = FundamentalType.StringType,
        output = FundamentalType.Float32
      )
    )
  )

  val dataSet1 = MantikHeader.pure(
    DataSetDefinition(
      bridge = Bridge.naturalBridge.mantikId,
      `type` = FundamentalType.StringType
    )
  )

  val dataSet2 = MantikHeader.pure(
    elements.DataSetDefinition(
      bridge = formatBridge.mantikId,
      `type` = FundamentalType.StringType
    )
  )

  val learning1 = MantikHeader.pure(
    TrainableAlgorithmDefinition(
      bridge = learningBridge.mantikId,
      trainingType = FundamentalType.Int32,
      statType = FundamentalType.StringType,
      `type` = FunctionType(
        input = FundamentalType.Int32,
        output = FundamentalType.BoolType
      )
    )
  )
}

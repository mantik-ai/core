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
package ai.mantik.engine.testutil

import ai.mantik.elements.{BridgeDefinition, MantikDefinition}
import ai.mantik.planner.repository.MantikArtifact

object TestArtifacts {

  val algoBridge1 = MantikArtifact.makeFromDefinition(
    BridgeDefinition(
      dockerImage = "algo_image1",
      suitable = Seq(MantikDefinition.AlgorithmKind)
    ),
    "algo_bridge1"
  )

  val trainingBridge1 = MantikArtifact.makeFromDefinition(
    BridgeDefinition(
      dockerImage = "trainable_image1",
      suitable = Seq(MantikDefinition.TrainableAlgorithmKind)
    ),
    "training_bridge1"
  )

  val trainedBridge1 = MantikArtifact.makeFromDefinition(
    BridgeDefinition(
      dockerImage = "trainable_image1",
      suitable = Seq(MantikDefinition.AlgorithmKind)
    ),
    "trained_bridge1"
  )
}

package ai.mantik.engine.testutil

import ai.mantik.elements.{ BridgeDefinition, MantikDefinition }
import ai.mantik.planner.repository.MantikArtifact

object TestArtifacts {

  val algoBridge1 = MantikArtifact.makeFromDefinition(
    BridgeDefinition(
      dockerImage = "algo_image1",
      suitable = Seq(MantikDefinition.AlgorithmKind
      )
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

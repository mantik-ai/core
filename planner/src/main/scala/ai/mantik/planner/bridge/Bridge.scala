package ai.mantik.planner.bridge

import ai.mantik.executor.model.docker.Container

case class AlgorithmBridge(
    stack: String,
    container: Container
)

case class TrainableAlgorithmBridge(
    stack: String,
    container: Container
)

case class FormatBridge(
    format: String,
    container: Option[Container] = None
)

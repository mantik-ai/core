package ai.mantik.planner.impl

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.funcational.SimpleFunction
import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import ai.mantik.planner.bridge.{ AlgorithmBridge, BridgeList, Bridges, FormatBridge, TrainableAlgorithmBridge }
import ai.mantik.repository.{ AlgorithmDefinition, DataSetDefinition, MantikDefinition, Mantikfile, TrainableAlgorithmDefinition }

object TestItems {

  val algorithm1 = Mantikfile.pure(
    AlgorithmDefinition(
      stack = "algorithm_stack1",
      `type` = SimpleFunction(
        input = FundamentalType.Int32,
        output = FundamentalType.StringType
      ),
      directory = Some("dir1")
    )
  )

  val dataSet1 = Mantikfile.pure(
    DataSetDefinition(
      format = "natural",
      `type` = FundamentalType.StringType
    )
  )

  val dataSet2 = Mantikfile.pure(
    DataSetDefinition(
      format = "format1",
      `type` = FundamentalType.StringType
    )
  )

  val learning1 = Mantikfile.pure(
    TrainableAlgorithmDefinition(
      stack = "training_stack1",
      trainingType = FundamentalType.Int32,
      statType = FundamentalType.StringType,
      `type` = SimpleFunction(
        input = FundamentalType.Int32,
        output = FundamentalType.BoolType
      )
    )
  )

  val algoPlugin1 = AlgorithmBridge("algorithm_stack1", Container("algorithm1_image"))
  val learningPlugin = TrainableAlgorithmBridge("training_stack1", Container("training1_image"))
  val naturalBridge = FormatBridge("natural", None)
  val format1 = FormatBridge("format1", Some(Container("format1_image")))

  val testBridges = BridgeList(
    Seq(algoPlugin1),
    Seq(learningPlugin),
    Seq(naturalBridge, format1),
    DockerConfig()
  )
}

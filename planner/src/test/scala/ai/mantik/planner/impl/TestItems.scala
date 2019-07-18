package ai.mantik.planner.impl

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements
import ai.mantik.elements.{ AlgorithmDefinition, DataSetDefinition, Mantikfile, TrainableAlgorithmDefinition }
import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import ai.mantik.planner.bridge.{ AlgorithmBridge, BridgeList, Bridges, FormatBridge, TrainableAlgorithmBridge }

object TestItems {

  val algorithm1 = Mantikfile.pure(
    AlgorithmDefinition(
      stack = "algorithm_stack1",
      `type` = FunctionType(
        input = FundamentalType.Int32,
        output = FundamentalType.StringType
      ),
      directory = Some("dir1")
    )
  )

  val algorithm2 = Mantikfile.pure(
    AlgorithmDefinition(
      stack = "algorithm_stack1",
      `type` = FunctionType(
        input = FundamentalType.StringType,
        output = FundamentalType.Float32
      ),
      directory = Some("dir2")
    )
  )

  val dataSet1 = Mantikfile.pure(
    DataSetDefinition(
      format = "natural",
      `type` = FundamentalType.StringType
    )
  )

  val dataSet2 = Mantikfile.pure(
    elements.DataSetDefinition(
      format = "format1",
      `type` = FundamentalType.StringType
    )
  )

  val learning1 = Mantikfile.pure(
    TrainableAlgorithmDefinition(
      stack = "training_stack1",
      trainingType = FundamentalType.Int32,
      statType = FundamentalType.StringType,
      `type` = FunctionType(
        input = FundamentalType.Int32,
        output = FundamentalType.BoolType
      )
    )
  )

  val algoPlugin1 = AlgorithmBridge("algorithm_stack1", Container("algorithm1_image"))
  val learningPlugin = TrainableAlgorithmBridge("training_stack1", Container("training1_image"))
  val naturalBridge = FormatBridge("natural", None)
  val format1 = FormatBridge("format1", Some(Container("format1_image")))
  val selectBridge = AlgorithmBridge("select", Container("select_image"))

  val testBridges = BridgeList(
    Seq(algoPlugin1, selectBridge),
    Seq(learningPlugin),
    Seq(naturalBridge, format1)
  )
}

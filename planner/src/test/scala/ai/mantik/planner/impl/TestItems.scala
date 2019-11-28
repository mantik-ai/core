package ai.mantik.planner.impl

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements
import ai.mantik.elements.{ AlgorithmDefinition, DataSetDefinition, Mantikfile, TrainableAlgorithmDefinition }
import ai.mantik.planner.repository.Bridge
import ai.mantik.planner.util.FakeBridges

object TestItems extends FakeBridges {

  val algorithm1 = Mantikfile.pure(
    AlgorithmDefinition(
      bridge = algoBridge.mantikId,
      `type` = FunctionType(
        input = FundamentalType.Int32,
        output = FundamentalType.StringType
      )
    )
  )

  val algorithm2 = Mantikfile.pure(
    AlgorithmDefinition(
      bridge = algoBridge.mantikId,
      `type` = FunctionType(
        input = FundamentalType.StringType,
        output = FundamentalType.Float32
      )
    )
  )

  val dataSet1 = Mantikfile.pure(
    DataSetDefinition(
      bridge = Bridge.naturalBridge.mantikId,
      `type` = FundamentalType.StringType
    )
  )

  val dataSet2 = Mantikfile.pure(
    elements.DataSetDefinition(
      bridge = formatBridge.mantikId,
      `type` = FundamentalType.StringType
    )
  )

  val learning1 = Mantikfile.pure(
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

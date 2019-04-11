package ai.mantik.planner.impl

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.funcational.SimpleFunction
import ai.mantik.planner.plugins.{ AlgorithmPlugin, NaturalFormatPlugin, Plugins, TrainableAlgorithmPlugin }
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

  val algoPlugin1 = new AlgorithmPlugin {
    override def stack: String = "algorithm_stack1"
    override def transformationContainerImage: String = "algorithm1_image"
  }
  val learningPlugin = new TrainableAlgorithmPlugin {
    override def stack: String = "training_stack1"
    override def trainableContainerImage: String = "training1_image"
  }

  val testPlugins = new Plugins(
    List(NaturalFormatPlugin),
    List(algoPlugin1),
    List(learningPlugin)
  )
}

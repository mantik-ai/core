package ai.mantik.planner

import ai.mantik.ds.DataType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.repository.{Mantikfile, TrainableAlgorithmDefinition}

/**
 * A trainable algorithm
 */
case class TrainableAlgorithm(
    source: Source,
    mantikfile: Mantikfile[TrainableAlgorithmDefinition],
) extends MantikItem {

  override type DefinitionType = TrainableAlgorithmDefinition

  def trainingDataType: DataType = mantikfile.definition.trainingType

  def statType: DataType = mantikfile.definition.statType

  def functionType: FunctionType = mantikfile.definition.`type`

  def train(trainingData: DataSet): (Algorithm, DataSet) = {
    if (trainingData.dataType != trainingDataType) {
      throw new RuntimeException(s"Training data is not as expected ${trainingDataType}")
    }

    val op = Operation.Training(this, trainingData)

    val trainedMantikfile = Mantikfile.generateTrainedMantikfile(mantikfile) match {
      case Left (err) => throw new RuntimeException(s"Could not generate trained mantikfle", err)
      case Right(ok) => ok
    }

    val algorithmResult = Algorithm(Source.OperationResult(op), trainedMantikfile)
    val statsResult = DataSet.natural(Source.OperationResult(op, 1), statType)
    (algorithmResult, statsResult)
  }
}

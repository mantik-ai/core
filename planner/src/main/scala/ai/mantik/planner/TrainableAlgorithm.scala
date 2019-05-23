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

  /** Train this [[TrainableAlgorithm]] with given trainingData.
    * @param cached if true, the result will be cached. This is important for if you want to access the training result and stats. */
  def train(trainingData: DataSet, cached: Boolean = true): (Algorithm, DataSet) = {
    val adapted = trainingData.autoAdaptOrFail(trainingDataType)

    val op = Operation.Training(this, adapted)

    val trainedMantikfile = Mantikfile.generateTrainedMantikfile(mantikfile) match {
      case Left (err) => throw new RuntimeException(s"Could not generate trained mantikfle", err)
      case Right(ok) => ok
    }

    val result = if (cached){
      Source.Cached(Source.OperationResult(op))
    } else {
      Source.OperationResult(op)
    }

    val algorithmResult = Algorithm(Source.Projection(result), trainedMantikfile)
    val statsResult = DataSet.natural(Source.Projection(result, 1), statType)
    (algorithmResult, statsResult)
  }
}

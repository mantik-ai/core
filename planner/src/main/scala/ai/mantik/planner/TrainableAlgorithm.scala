package ai.mantik.planner

import ai.mantik.ds.DataType
import ai.mantik.ds.element.SingleElementBundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ Mantikfile, TrainableAlgorithmDefinition }
import ai.mantik.planner.repository.Bridge

/**
 * A trainable algorithm
 */
final case class TrainableAlgorithm(
    core: MantikItemCore[TrainableAlgorithmDefinition],
    trainedBridge: Bridge
) extends BridgedMantikItem {

  override type DefinitionType = TrainableAlgorithmDefinition
  override type OwnType = TrainableAlgorithm

  def trainingDataType: DataType = mantikfile.definition.trainingType

  def statType: DataType = mantikfile.definition.statType

  def functionType: FunctionType = mantikfile.definition.`type`

  /**
   * Train this [[TrainableAlgorithm]] with given trainingData.
   * @param cached if true, the result will be cached. This is important for if you want to access the training result and stats.
   */
  def train(trainingData: DataSet, cached: Boolean = true): (Algorithm, DataSet) = {
    val adapted = trainingData.autoAdaptOrFail(trainingDataType)

    val op = Operation.Training(this, adapted)

    val trainedMantikfile = Mantikfile.generateTrainedMantikfile(mantikfile) match {
      case Left(err) => throw new RuntimeException(s"Could not generate trained mantikfle", err)
      case Right(ok) => ok
    }

    val result = if (cached) {
      PayloadSource.Cached(PayloadSource.OperationResult(op))
    } else {
      PayloadSource.OperationResult(op)
    }

    val algorithmResult = Algorithm(
      Source.constructed(PayloadSource.Projection(result)),
      trainedMantikfile,
      trainedBridge
    )
    val statsResult = DataSet.natural(Source.constructed(PayloadSource.Projection(result, 1)), statType)
    (algorithmResult, statsResult)
  }

  override protected def withCore(core: MantikItemCore[TrainableAlgorithmDefinition]): TrainableAlgorithm = {
    copy(core = core)
  }
}

object TrainableAlgorithm {

  def apply(
    source: Source,
    mantikfile: Mantikfile[TrainableAlgorithmDefinition],
    bridge: Bridge,
    trainedBridge: Bridge
  ): TrainableAlgorithm = {
    TrainableAlgorithm(MantikItemCore(source, mantikfile, bridge), trainedBridge)
  }
}

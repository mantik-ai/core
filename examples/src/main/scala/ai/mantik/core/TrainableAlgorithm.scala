package ai.mantik.core

import ai.mantik.ds.DataType
import ai.mantik.ds.funcational.FunctionType

case class TrainableAlgorithm(
    source: Source,
    typeTransformation: TrainableTypeTransformation,
    statsTransformation: FunctionType
) extends MantikItem {

  def train(trainingData: DataSet, validation: Option[DataSet]): (Transformation, DataSet) = {
    validation.foreach { v =>
      require(v.dataType == trainingData.dataType, "Training and validation data must be of same type")
    }
    val trainingPlan = Action.TrainAlgorithmAction(this, trainingData, validation)
    val resultingType = typeTransformation.forceResulting(trainingData.dataType)
    val resultingStatType = statsTransformation.applies(trainingData.dataType).right.getOrElse(
      throw new RuntimeException(s"Could not apply stats transformation")
    )
    Transformation(
      Source.TrainedResultTransformation(trainingPlan), resultingType
    ) -> DataSet(
        Source.TrainingStats(trainingPlan), resultingStatType
      )
  }
}

// TODO: Remove me and replace with DS Type.
sealed trait TrainableTypeTransformation {
  /** Decides the final type of the application of the trainable algorithm after applying a type of learning data to it. */
  def resulting(inputType: DataType): Either[String, FunctionType]

  def forceResulting(inputType: DataType): FunctionType = {
    resulting(inputType) match {
      case Left(error) => throw new RuntimeException(s"Types do not match ${error}")
      case Right(ok)   => ok
    }
  }
}
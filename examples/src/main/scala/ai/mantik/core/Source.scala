package ai.mantik.core

import ai.mantik.core.Action.TrainAlgorithmAction
import ai.mantik.ds.element.Bundle
import ai.mantik.repository._

/** Represents the way a core model is derived. */
sealed trait Source

object Source {

  /** A fixed location in the repository. */
  case class Loaded(artefact: MantikArtefact) extends Source

  /** A Transformation has been applied on this data set. */
  case class AppliedTransformation(dataSet: DataSet, transformation: Transformation) extends Source

  /** Is generated as being the result of an algorithm training. */
  case class TrainedResultTransformation(action: TrainAlgorithmAction) extends Source

  /** Stats being generated as a result of an algorithm training. */
  case class TrainingStats(action: TrainAlgorithmAction) extends Source

  /** A DataSet defined from a natural bundle. */
  case class Literal(bundle: Bundle) extends Source
}

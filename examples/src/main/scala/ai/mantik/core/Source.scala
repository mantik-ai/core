package ai.mantik.core

import ai.mantik.ds.element.Bundle

/** Defines an operation. */
sealed trait Operation {
  def resultCount: Int = 1
}

case object Operation {

  /** An algorithm was applied. */
  case class Application(algorithm: Algorithm, argument: DataSet) extends Operation

  /** A Training Operation. */
  case class Training(algorithm: TrainableAlgorithm, trainingData: DataSet) extends Operation {
    override def resultCount: Int = 2 // stats, trained algorithm
  }
}

/** Represents the way a [[MantikItem]] gets their Payload Data from. */
sealed trait Source

object Source {

  /** There is no payload. */
  case object Empty extends Source

  /** A fixed location in the file repository. */
  case class Loaded(fileId: String) extends Source

  /** A fixed data block. */
  sealed trait Literal extends Source

  /** A Literal which contains a bundle. */
  case class BundleLiteral(content: Bundle) extends Literal

  /**
   * It's the result of some operation.
   * If projection is > 0, the non main result is used.
   */
  case class OperationResult(op: Operation, projection: Int = 0) extends Source
}

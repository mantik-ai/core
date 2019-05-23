package ai.mantik.planner

import java.util.UUID

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

/** Represents the way [[MantikItem]](s) gets their Payload Data from. */
sealed abstract class Source(val resultCount: Int)

object Source {

  /** There is no payload. */
  case object Empty extends Source(0)

  /** A fixed location in the file repository. */
  case class Loaded(fileId: String, contentType: String) extends Source(1)

  /** A fixed data block. */
  sealed abstract class Literal extends Source(1)

  /** A Literal which contains a bundle. */
  case class BundleLiteral(content: Bundle) extends Literal()

  /**
   * It's the result of some operation.
   * If projection is > 0, the non main result is used.
   */
  case class OperationResult(op: Operation) extends Source(op.resultCount)

  /** Projects one of multiple results of the source. */
  case class Projection(source: Source, projection: Int = 0) extends Source(1) {
    require(projection >= 0 && projection < source.resultCount)
  }

  /** A Cached source value. */
  case class Cached(
      source: Source
  ) extends Source(source.resultCount) {

    /** Identifies the source elements. */
    private[mantik] val cacheGroup: CacheKeyGroup = {
      (for (_ <- 0 until source.resultCount) yield {
        UUID.randomUUID()
      }).toList
    }
  }
}

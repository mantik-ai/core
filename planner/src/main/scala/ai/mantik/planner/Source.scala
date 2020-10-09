package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.sql.Select
import ai.mantik.elements.{ ItemId, NamedMantikId }

/** Defines where a MantikItem comes from. */
case class Source(
    definition: DefinitionSource,
    payload: PayloadSource
) {
  /** Creates a derived source with the same payload. */
  def derive: Source = Source(DefinitionSource.Derived(definition), payload)
}

object Source {

  /** Creates a source for a constructed element. */
  def constructed(payloadSource: PayloadSource = PayloadSource.Empty): Source = Source(
    DefinitionSource.Constructed(), payloadSource
  )
}

/** Represents the way [[MantikItem]](s) get their Definition data from. */
sealed trait DefinitionSource {
  /** Returns true if the source is indicating that the name is stored. */
  def nameStored: Boolean = false
  /** Returns tre if the source is indicating that the item is stored. */
  def itemStored: Boolean = false
  /** Returns a name if the source indicates that the item has one. */
  def name: Option[NamedMantikId] = None
  /** Returns an item id if the source indicates that the item has one. */
  def storedItemId: Option[ItemId] = None
}

object DefinitionSource {
  /** The item was loaded from the repository. */
  case class Loaded(mantikId: Option[NamedMantikId], itemId: ItemId) extends DefinitionSource {
    override def nameStored: Boolean = mantikId.isDefined

    override def itemStored: Boolean = true

    override def name: Option[NamedMantikId] = mantikId

    override def storedItemId: Option[ItemId] = Some(itemId)
  }

  /** The item was artificially constructed (e.g. literals, calculations, ...) */
  case class Constructed() extends DefinitionSource

  /** The item was somehow derived from another one (e.g. changing MantikHeader) */
  case class Derived(other: DefinitionSource) extends DefinitionSource

  /** The item was tagged with a name. */
  case class Tagged(namedId: NamedMantikId, from: DefinitionSource) extends DefinitionSource {
    override def nameStored: Boolean = false

    override def itemStored: Boolean = from.itemStored

    override def name: Option[NamedMantikId] = Some(namedId)

    override def storedItemId: Option[ItemId] = from.storedItemId
  }
}

/** Defines an operation. */
sealed trait Operation {
  def resultCount: Int = 1
}

case object Operation {

  /** An algorithm was applied. */
  case class Application(algorithm: ApplicableMantikItem, argument: DataSet) extends Operation

  /** A Training Operation. */
  case class Training(algorithm: TrainableAlgorithm, trainingData: DataSet) extends Operation {
    override def resultCount: Int = 2 // stats, trained algorithm
  }

  /** A Simple select operation with one input */
  case class SelectOperation(select: Select, argument: DataSet) extends Operation
}

/** Represents the way [[MantikItem]](s) gets their Payload Data from. */
sealed trait PayloadSource {
  def resultCount: Int = 1
}

object PayloadSource {

  /** There is no payload. */
  case object Empty extends PayloadSource {
    override def resultCount = 0
  }

  /** A fixed location in the file repository. */
  case class Loaded(fileId: String, contentType: String) extends PayloadSource

  /** A fixed data block. */
  sealed abstract class Literal extends PayloadSource

  /** A Literal which contains a bundle. */
  case class BundleLiteral(content: Bundle) extends Literal()

  /**
   * It's the result of some operation.
   * If projection is > 0, the non main result is used.
   */
  case class OperationResult(op: Operation) extends PayloadSource {
    override def resultCount: Int = op.resultCount
  }

  /** Projects one of multiple results of the source. */
  case class Projection(source: PayloadSource, projection: Int = 0) extends PayloadSource {
    require(projection >= 0 && projection < source.resultCount)
  }

  /**
   * A Cached source value.
   * @param siblings the parallel elements which are evaluated at the same time.
   */
  case class Cached(
      source: PayloadSource,
      siblings: Vector[ItemId]
  ) extends PayloadSource {
    override def resultCount: Int = source.resultCount

    require(source.resultCount == siblings.size, "Results must match items")
  }
}

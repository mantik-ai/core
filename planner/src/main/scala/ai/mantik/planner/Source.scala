package ai.mantik.planner

import java.util.UUID

import ai.mantik.ds.element.Bundle
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

  /** The item was somehow derived from another one (e.g. changing Mantikfile) */
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
}

/** Represents the way [[MantikItem]](s) gets their Payload Data from. */
sealed abstract class PayloadSource(val resultCount: Int) {
  /** Returns the cache key for this payload if applicable. */
  def cachedGroup: Option[CacheKeyGroup] = None
}

object PayloadSource {

  /** There is no payload. */
  case object Empty extends PayloadSource(0)

  /** A fixed location in the file repository. */
  case class Loaded(fileId: String, contentType: String) extends PayloadSource(1)

  /** A fixed data block. */
  sealed abstract class Literal extends PayloadSource(1)

  /** A Literal which contains a bundle. */
  case class BundleLiteral(content: Bundle) extends Literal()

  /**
   * It's the result of some operation.
   * If projection is > 0, the non main result is used.
   */
  case class OperationResult(op: Operation) extends PayloadSource(op.resultCount)

  /** Projects one of multiple results of the source. */
  case class Projection(source: PayloadSource, projection: Int = 0) extends PayloadSource(1) {
    require(projection >= 0 && projection < source.resultCount)

    /** Returns the cache key for this payload if applicable. */
    override def cachedGroup: Option[CacheKeyGroup] = source.cachedGroup.flatMap {
      case group if group.isDefinedAt(projection) => Some(List(group(projection)))
      case _                                      => None
    }
  }

  /** A Cached source value. */
  case class Cached(
      source: PayloadSource
  ) extends PayloadSource(source.resultCount) {

    /** Identifies the source elements. */
    private[mantik] val cacheGroup: CacheKeyGroup = {
      (for (_ <- 0 until source.resultCount) yield {
        UUID.randomUUID()
      }).toList
    }

    override def cachedGroup: Option[CacheKeyGroup] = Some(cacheGroup)
  }
}

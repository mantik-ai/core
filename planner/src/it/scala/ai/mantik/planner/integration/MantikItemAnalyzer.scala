package ai.mantik.planner.integration

import ai.mantik.planner._

/** Helper for analyzing MantikItems
  * TODO: Perhaps this could go into the MantikItem's API
  * but right now we only need this for testing. */
case class MantikItemAnalyzer (
  item: MantikItem,
)(implicit context: Context) {

  /** Return true if the item is requested for caching
    * (This doesn't have to mean that the cache is evaluated) */
  def isCached: Boolean = cacheGroup(item).isDefined

  /** Return true if the item's payload is inside the cache. */
  def isCacheEvaluated: Boolean = {
    cacheGroup(item) match {
      case Some(group) =>
        group.forall { cacheKey =>
          context.planExecutor.cachedFile(cacheKey).isDefined
        }
      case None => false
    }
  }

  def cacheFile: Option[FileId] = {
    cacheGroup(item) match {
      case None => None
      case Some(List(one)) => context.planExecutor.cachedFile(one)
      case Some(multiple) =>
        // can't be cached, should also not happen
        None
    }
  }

  private def cacheGroup(item: MantikItem): Option[CacheKeyGroup] = {
    cacheGroup(item.payloadSource)
  }

  private def cacheGroup(payloadSource: PayloadSource): Option[CacheKeyGroup] = {
    payloadSource match {
      case c: PayloadSource.Cached => Some(c.cacheGroup)
      case p: PayloadSource.Projection =>
        cacheGroup(p.source).map { baseGroup =>
          List(baseGroup(p.projection))
        }
      case _ => None
    }
  }
}

package ai.mantik.planner.integration

import ai.mantik.planner.{Context, MantikItem, PayloadSource}

/** Helper for analyzing MantikItems
  * TODO: Perhaps this could go into the MantikItem's API
  * but right now we only need this for testing. */
case class MantikItemAnalyzer (
  item: MantikItem,
)(implicit context: Context) {

  /** Return true if the item is requested for caching
    * (This doesn't have to mean that the cache is evaluated) */
  def isCached: Boolean = item.core.source.payload.cachedGroup.isDefined

  /** Return true if the item's payload is inside the cache. */
  def isCacheEvaluated: Boolean = {
    item.core.source.payload.cachedGroup match {
      case Some(group) =>
        group.forall { cacheKey =>
          context.planExecutor.cachedFile(cacheKey).isDefined
        }
      case None => false
    }
  }
}

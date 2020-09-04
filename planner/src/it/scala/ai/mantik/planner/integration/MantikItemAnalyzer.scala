package ai.mantik.planner.integration

import ai.mantik.planner._

/** Helper for analyzing MantikItems
  * TODO: Perhaps this could go into the MantikItem's API
  * but right now we only need this for testing.
  */
@deprecated("mnp", "Can be solved via MantikState")
case class MantikItemAnalyzer (
  item: MantikItem,
)(implicit context: PlanningContext) {

  /** Return true if the item is requested for caching
    * (This doesn't have to mean that the cache is evaluated) */
  def isCached: Boolean = {
    def check(payloadSource: PayloadSource): Boolean = {
      payloadSource match {
        case _: PayloadSource.Cached => true
        case p: PayloadSource.Projection => check(p.source)
        case _ => false
      }
    }
    check(item.core.source.payload)
  }

  /** Return true if the item's payload is inside the cache. */
  def isCacheEvaluated: Boolean = {
    cacheFile.isDefined
  }

  def cacheFile: Option[FileId] = {
    context.state(item).cacheFile
  }
}

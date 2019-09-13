package ai.mantik.planner.impl

import ai.mantik.planner.impl.exec.FileCache
import ai.mantik.planner.{ CacheKeyGroup, FileId }
import com.google.inject.ImplementedBy

/** Provides information, if some cache group is cached. */
@ImplementedBy(classOf[FileCache])
trait CachedFiles {

  /** Checks if a cacheKey Group is fully cached and returns the file ids if it is. */
  def cached(cacheKeyGroup: CacheKeyGroup): Option[List[FileId]]

}

object CachedFiles {
  /** An Empty cache. */
  def empty: CachedFiles = new CachedFiles {
    override def cached(cacheKeyGroup: CacheKeyGroup): Option[List[String]] = None
  }
}
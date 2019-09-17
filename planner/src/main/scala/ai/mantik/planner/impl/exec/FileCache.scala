package ai.mantik.planner.impl.exec

import ai.mantik.planner.{ CacheKey, CacheKeyGroup, FileId }
import ai.mantik.planner.impl.CachedFiles

import scala.collection.mutable
import cats.implicits._
import javax.inject.Singleton

/** Associates [[CacheKey]] to Cached Files. */
@Singleton
class FileCache extends CachedFiles {

  private object lock
  private val entries = mutable.Map.empty[CacheKey, String]

  /** Add a cached entry to the cache. */
  def add(key: CacheKey, fileId: String): Unit = {
    lock.synchronized {
      entries += (key -> fileId)
    }
  }

  /** Tries to resolve the cached value. */
  def get(key: CacheKey): Option[String] = {
    lock.synchronized {
      entries.get(key)
    }
  }

  def remove(key: CacheKey): Unit = {
    lock.synchronized {
      entries.remove(key)
    }
  }

  override def cached(cacheKeyGroup: CacheKeyGroup): Option[List[FileId]] = {
    lock.synchronized {
      cacheKeyGroup.map(entries.get).sequence
    }
  }
}

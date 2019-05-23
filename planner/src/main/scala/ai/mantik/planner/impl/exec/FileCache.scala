package ai.mantik.planner.impl.exec

import ai.mantik.planner.CacheKey

import scala.collection.mutable

/** Associates [[CacheKey]] to Cached Files. */
class FileCache {

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
}

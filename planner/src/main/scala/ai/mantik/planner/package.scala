package ai.mantik

import java.util.UUID

package object planner {

  /** A Single cached element. */
  private[mantik] type CacheKey = UUID

  /** A group of cached element which can either be resolved all together or nothing of them. */
  private[mantik] type CacheKeyGroup = List[CacheKey]

  /** Identifies a slot in the memory during execution of plans. */
  private[mantik] type MemoryId = String

  /** Identifies a file */
  private[mantik] type FileId = String
}

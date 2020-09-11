package ai.mantik

package object planner {
  /** Identifies a slot in the memory during execution of plans. */
  private[mantik] type MemoryId = String

  /** Identifies a file */
  private[mantik] type FileId = String
}

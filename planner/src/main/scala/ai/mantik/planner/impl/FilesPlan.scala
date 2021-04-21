package ai.mantik.planner.impl

import ai.mantik.planner.{PlanFileReference, PlanOp}

/** References a file with it's associated content type. */
private[impl] case class PlanFileWithContentType(
    ref: PlanFileReference,
    contentType: String
)

/**
  * References multiple data streams available as files.
  * @param preOp the plan necessary to make it available.
  * @param files the files which make it available.
  */
private[impl] case class FilesPlan(
    preOp: PlanOp[Unit] = PlanOp.Empty,
    files: IndexedSeq[PlanFileWithContentType] = IndexedSeq.empty
) {

  /** Return the file references. */
  def fileRefs: IndexedSeq[PlanFileReference] = files.map(_.ref)
}

package ai.mantik.planner.impl.exec

import ai.mantik.planner.PlanExecutor.InvalidPlanException
import ai.mantik.planner.PlanFileReference
import ai.mantik.planner.repository.FileRepository.{ FileGetResult, FileStorageResult }

/**
 * Handles Open Files during Plan Execution, part of [[MnpPlanExecutor]].
 *
 * Generated by [[ExecutionOpenFilesBuilder]]
 *
 * @param readFiles files for reading
 * @param writeFiles files for writing
 */
private[impl] case class ExecutionOpenFiles(
    private[exec] val readFiles: Map[PlanFileReference, FileGetResult] = Map.empty,
    private[exec] val writeFiles: Map[PlanFileReference, FileStorageResult] = Map.empty
) {

  lazy val fileIds: Map[PlanFileReference, String] = {
    readFiles.mapValues(_.fileId) ++ writeFiles.mapValues(_.fileId)
  }

  def resolveFileWrite(fileReference: PlanFileReference): FileStorageResult = {
    writeFiles.getOrElse(fileReference, throw new InvalidPlanException(s"File to write $fileReference is not opened"))
  }

  def resolveFileRead(fileReference: PlanFileReference): FileGetResult = {
    readFiles.getOrElse(fileReference, throw new InvalidPlanException(s"File to read $fileReference is not opened"))
  }

  def resolveFileId(fileReference: PlanFileReference): String = {
    fileIds.getOrElse(fileReference, throw new InvalidPlanException(s"File $fileReference has no file id associated"))
  }
}

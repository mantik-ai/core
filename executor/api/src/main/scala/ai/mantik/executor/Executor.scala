package ai.mantik.executor

import ai.mantik.executor.model.{ Job, JobStatus }

import scala.concurrent.Future

/** Defines the interface for the Executor. */
trait Executor {

  /** Schedule a job, returns a future on it's id. */
  def schedule(job: Job): Future[String]

  /** Returns the status of a job. */
  def status(isolationSpace: String, id: String): Future[JobStatus]

  /** Returns the logs of a job. */
  def logs(isolationSpace: String, id: String): Future[String]
}

package ai.mantik.executor

import ai.mantik.executor.model.{ DeployServiceRequest, DeployServiceResponse, DeployedServicesQuery, DeployedServicesResponse, Job, JobStatus, PublishServiceRequest, PublishServiceResponse }

import scala.concurrent.Future

/** Defines the interface for the Executor. */
trait Executor {

  /** Schedule a job, returns a future on it's id. */
  def schedule(job: Job): Future[String]

  /** Returns the status of a job. */
  def status(isolationSpace: String, id: String): Future[JobStatus]

  /** Returns the logs of a job. */
  def logs(isolationSpace: String, id: String): Future[String]

  /**
   * Publish a external service to the cluster.
   * Note: this only for simplifying local deployments.
   */
  def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse]

  /**
   * Deploy a single service.
   */
  def deployService(deployServiceRequest: DeployServiceRequest): Future[DeployServiceResponse]

  /**
   * Query for deployed services.
   */
  def queryDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[DeployedServicesResponse]

  /**
   * Delete deployed services.
   * @return number of services deleted.
   */
  def deleteDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[Int]

  /** Returns the name and version string of the server (displayed on about page). */
  def nameAndVersion: String
}

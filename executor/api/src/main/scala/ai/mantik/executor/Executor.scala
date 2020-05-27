package ai.mantik.executor

import ai.mantik.componently.Component
import ai.mantik.executor.model.{ DeployServiceRequest, DeployServiceResponse, DeployedServicesQuery, DeployedServicesResponse, GrpcProxy, Job, JobStatus, ListWorkerRequest, ListWorkerResponse, PublishServiceRequest, PublishServiceResponse, StartWorkerRequest, StartWorkerResponse, StopWorkerRequest, StopWorkerResponse }
import io.grpc.{ ManagedChannelBuilder, ManagedChannelProvider }

import scala.concurrent.Future

/** Defines the interface for the Executor. */
trait Executor extends Component {

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
  def nameAndVersion: Future[String]

  // New API
  /** Returns the gRpc proxy which enables the Engine to communicate with MNP Nodes. */
  def grpcProxy(isolationSpace: String): Future[GrpcProxy]

  /** Start a new Worker. */
  def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse]

  /** List workers. */
  def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse]

  /** Stop worker(s). */
  def stopWorker(stopWorkerRequest: StopWorkerRequest): Future[StopWorkerResponse]
}

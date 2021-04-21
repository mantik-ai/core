package ai.mantik.executor

import ai.mantik.componently.Component
import ai.mantik.executor.model._

import scala.concurrent.Future

/** Defines the interface for the Executor. */
trait Executor extends Component {

  /**
    * Publish a external service to the cluster.
    * Note: this only for simplifying local deployments.
    */
  def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse]

  /** Returns the name and version string of the server (displayed on about page). */
  def nameAndVersion: Future[String]

  /** Returns the gRpc proxy which enables the Engine to communicate with MNP Nodes. */
  def grpcProxy(isolationSpace: String): Future[GrpcProxy]

  /** Start a new Worker. */
  def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse]

  /** List workers. */
  def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse]

  /** Stop worker(s). */
  def stopWorker(stopWorkerRequest: StopWorkerRequest): Future[StopWorkerResponse]
}

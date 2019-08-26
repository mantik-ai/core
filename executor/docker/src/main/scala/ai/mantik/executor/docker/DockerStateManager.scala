package ai.mantik.executor.docker

import java.util
import java.util.concurrent.ConcurrentHashMap

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.executor.docker.DockerStateManager.{ CommonInfo, Info, ProcessState }
import ai.mantik.executor.docker.api.DockerClient
import ai.mantik.executor.docker.api.structures.ListContainerRequestFilter
import com.typesafe.scalalogging.Logger

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

/**
 * Holds extra information about Docker Jobs
 * E.g. additional error information.
 *
 * Jobs handling is not completely stateless (e.g. during their initialisation phase).
 * Also jobs, can have errors associated (e.g. missing image), which is hard to encode
 * inside the coordinator image.
 *
 * There fore this in-memory-representation provides extra information.
 */
class DockerStateManager(initial: Map[String, Info]) {
  val logger = Logger(getClass)

  private val information = new ConcurrentHashMap[String, Info]()
  information.putAll(initial.asJava)

  /** Set additional information of a process. */
  def setState(id: String, info: ProcessState): Unit = {
    information.computeIfPresent(
      id,
      (_, existing) => existing.copy(state = info)
    )
    logger.info(s"New state for ${id} ${info}")
  }

  def updateCommon(id: String, f: CommonInfo => CommonInfo): Unit = {
    information.computeIfPresent(id, (_, existing) => existing.copy(commonInfo = f(existing.commonInfo)))
  }

  /** Returns current information of a process. */
  def get(jobId: String): Option[Info] = {
    Option(information.get(jobId))
  }

  /** Reserve a id, returns true if it was reserved */
  def reserve(jobId: String, value: Info): Boolean = {
    Option(information.putIfAbsent(jobId, value)).isEmpty
  }

  /**
   * Create a copy of the current state.
   * The view doesn't need to be a consistent snapshot
   */
  def copy(): Map[String, Info] = {
    // According to doc, the iterator should not throw for concurrent modifications.
    // So it's basically eventually consistent.
    information.entrySet()
      .iterator()
      .asScala.map { p => p.getKey -> p.getValue }.toMap
  }

  /** Like copy, but filters for a specific type of ProcessInfo. */
  def dumpOfType[T <: ProcessState: ClassTag]: Vector[(String, CommonInfo, T)] = {
    val iterator = information.entrySet()
      .iterator()
      .asScala.map { p => p.getKey -> p.getValue }
    iterator.collect {
      case (key, Info(c, x: T)) => (key, c, x)
    }.toVector
  }

  /** Remove an element. */
  def remove(id: String): Unit = {
    information.remove(id)
  }
}

object DockerStateManager {
  private val logger = Logger(getClass)

  /** Initialize DockerState Manager from running containers. */
  def initialize(dockerClient: DockerClient)(implicit akkaRuntime: AkkaRuntime): Future[DockerStateManager] = {
    import ai.mantik.componently.AkkaHelper._
    dockerClient.listContainersFiltered(
      false, ListContainerRequestFilter.forLabelKeyValue(
        DockerConstants.ManagedByLabelName -> DockerConstants.ManabedByLabelValue
      )
    ).map { runningContainers =>
        // Only services are recovered
        // Jobs are not not yet meant to be tracked after executor restart.

        val services = for {
          container <- runningContainers
          containerNameDockerLike <- container.Names.headOption
          containerName = containerNameDockerLike.stripPrefix("/") // docker loves to prefix "/" to it's containers.
          if container.Labels.get(DockerConstants.TypeLabelName).contains(DockerConstants.ServiceType)
          id <- container.Labels.get(DockerConstants.IdLabelName)
          isolationSpace <- container.Labels.get(DockerConstants.IsolationSpaceLabelName)
          userServiceId <- container.Labels.get(DockerConstants.UserIdLabelName)
          portString <- container.Labels.get(DockerConstants.PortLabel)
          port <- Try(portString.toInt).toOption
          internalUrl = DockerService.formatInternalUrl(containerName, port)
          info = Info(
            CommonInfo(isolationSpace),
            ServiceInstalled(userServiceId, internalUrl)
          )
        } yield (id -> info)
        new DockerStateManager(services.toMap)
      }
  }

  /** Try to initialize multiple times. Useful when docker is not immediately reachable. */
  def retryingInitialize(dockerClient: DockerClient)(implicit akkaRuntime: AkkaRuntime): Future[DockerStateManager] = {
    import ai.mantik.componently.AkkaHelper._
    val timeout = 60.seconds
    val retryTime = 5.seconds
    FutureHelper.tryMultipleTimes(timeout, retryTime) {
      initialize(dockerClient).transform {
        case Success(value) => Success(Some(value))
        case Failure(error) =>
          logger.warn(s"Can't initialize docker state", error)
          Success(None)
      }
    }
  }

  /** Common info about jobs/services. */
  case class Info(
      commonInfo: CommonInfo,
      state: ProcessState
  )

  /** Common info about jobs/services. */
  case class CommonInfo(
      isolationSpace: String
  )

  sealed trait ProcessState
  /** Job structure generated and name claimed */
  case class JobPending() extends ProcessState
  /** Containers are created and pulled if necessary. */
  case class JobCreated() extends ProcessState
  /** Container Payload is downloaded, they are ready to run. */
  case class ContainersInitialized() extends ProcessState
  /** Containers are running */
  case class JobRunning() extends ProcessState
  /** Something failed */
  case class JobFailed(msg: String, log: LogContent = LogContent()) extends ProcessState
  /** Job suceeded. */
  case class JobSucceeded(log: LogContent = LogContent()) extends ProcessState

  /** A Service which is pending. */
  case class ServicePending() extends ProcessState
  /** Necessary containers have been created */
  case class ServiceCreated() extends ProcessState
  /** Payload is downloaded, ready to run. */
  case class ServiceInitialized() extends ProcessState
  case class ServiceInstalled(userServiceId: String, internalUrl: String) extends ProcessState
  case class ServiceFailed(msg: String) extends ProcessState

  /** Helper containing log output without flushing the whole own log when printing. */
  case class LogContent(lines: Option[String] = None) {
    override def toString: String = {
      val last = lines.map(stripWhitespace)
      last match {
        case Some(last) if last.length > 100 => "..." + last.takeRight(100)
        case Some(last)                      => last
        case None                            => "<no log>"
      }
    }
  }

  private def stripWhitespace(in: String): String = {
    in.replaceAll("\\s", " ")
  }
}

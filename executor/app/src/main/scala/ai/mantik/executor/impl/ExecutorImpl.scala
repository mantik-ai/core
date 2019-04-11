package ai.mantik.executor.impl

import java.time.{ Clock, Instant }
import java.util.UUID

import ai.mantik.executor.Errors.NotFoundException
import ai.mantik.executor.impl.tracker.KubernetesTracker
import ai.mantik.executor.model.{ GraphAnalysis, Job, JobState, JobStatus }
import ai.mantik.executor.{ Config, Errors, Executor }
import akka.actor.{ ActorSystem, Cancellable }
import com.typesafe.scalalogging.Logger
import skuber.Pod
import skuber.json.batch.format._
import skuber.json.format._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

class ExecutorImpl(config: Config, ops: K8sOperations)(
    implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem,
    clock: Clock
) extends Executor {
  val logger = Logger(getClass)
  val tracker = new KubernetesTracker(config, ops)

  override def schedule(job: Job): Future[String] = {
    val jobId = UUID.randomUUID().toString
    val namespace = namespaceForIsolationSpace(job.isolationSpace)
    logger.info(s"Creating job ${jobId} in namespace ${namespace}...")
    ops.ensureNamespace(namespace).flatMap { namespacedClient =>
      logger.debug(s"Namespace for job ${job.isolationSpace}/${jobId}: ${namespace} ensured...")
      val converter = try {
        new KubernetesJobConverter(config, job, jobId)
      } catch {
        case e: GraphAnalysis.AnalyzerException =>
          logger.warn(s"Graph analysis failed", e)
          throw new Errors.BadRequestException(s"Graph analysis failed: e: ${e.getMessage}")
      }

      // Pods are not reachable by it's name but by their IP Address, however we must first start them to get their IP Address.
      ops.startPodsAndGetIpAdresses(Some(namespace), converter.pods).flatMap { podsWithIpAdresses =>
        logger.debug(s"Created pods for ${jobId}: ${podsWithIpAdresses.values}")

        val configMap = converter.configuration(podsWithIpAdresses)
        ops.create(Some(namespace), configMap).flatMap { configMap =>
          logger.debug(s"Created ConfigMap for ${jobId}")
          val job = converter.convertCoodinator
          ops.create(Some(namespace), job).map { job =>
            logger.info(s"Created ${podsWithIpAdresses.size} pods, config map and job ${job.name}")
            tracker.subscribe(job)
            jobId
          }
        }
      }
    }
  }

  private def namespaceForIsolationSpace(isolationSpace: String): String = {
    // TODO: Escape invalid characters.
    config.namespacePrefix + isolationSpace
  }

  override def status(isolationSpace: String, id: String): Future[JobStatus] = {
    val namespace = namespaceForIsolationSpace(isolationSpace)
    ops.getJobById(Some(namespace), id).map {
      case None =>
        throw new NotFoundException(s"Job ${id} not found in isolationSpace ${isolationSpace}")
      case Some(job) =>
        job.status.map { status =>
          logger.info(s"Decoding job state ${status}")
          status.active match {
            case Some(a) if a > 0 => JobStatus(JobState.Running)
            case _ =>
              status.failed match {
                case Some(x) if x > 0 => JobStatus(JobState.Failed)
                case _ =>
                  status.succeeded match {
                    case Some(x) if x > 0 => JobStatus(JobState.Finished)
                    case _ =>
                      logger.warn(s"Could not decode ${status}")
                      JobStatus(JobState.Running)
                  }
              }
          }
        }.getOrElse {
          logger.info(s"No job state found for ${id} in ${namespace}, probably pending")
          JobStatus(
            state = JobState.Pending
          )
        }
    }
  }

  override def logs(isolationSpace: String, id: String): Future[String] = {
    val namespace = namespaceForIsolationSpace(isolationSpace)
    ops.getJobLog(Some(namespace), id)
  }

  private val checkPodCancellation = config.checkPodInterval match {
    case f: FiniteDuration =>
      actorSystem.scheduler.schedule(f, f)(checkPods())
    case _ => // nothign
      Cancellable.alreadyCancelled
  }

  private def checkPods(): Unit = {
    logger.debug("Checking Pods")
    val timestamp = clock.instant()
    checkBrokenImagePods(timestamp)
  }

  private def checkBrokenImagePods(timestamp: Instant): Unit = {
    val borderTime = timestamp.minusSeconds(config.podPullImageTimeout.toSeconds)
    ops.getAllManagedPendingPods().foreach { pendingPods =>
      pendingPods.foreach {
        case (namespaceName, pendingPods) =>
          val isMissingImage = pendingPods.filter(pod => isOldImageNotFound(borderTime, pod))
          isMissingImage.foreach { pod =>
            handleBrokenImagePod(pod)
          }
      }
    }
  }

  private def handleBrokenImagePod(pod: Pod): Unit = {
    val maybeJobId = pod.metadata.labels.get(KubernetesJobConverter.JobIdLabel)
    logger.info(s"Detected broken image in ${pod.namespace}/${pod.name}, jobId=${maybeJobId}")
    logger.info(s"Deleting Pod...")
    ops.delete[Pod](Some(pod.namespace), pod.name)
    logger.info(s"Cancelling Job...")
    maybeJobId.foreach { jobId =>
      cancelPods(pod.namespace, jobId, "Pod could not find image")
    }
  }

  private def cancelPods(namespace: String, jobId: String, reason: String): Unit = {
    ops.getPodsByJobId(Some(namespace), jobId).foreach { pods =>
      pods.foreach { pod =>
        ops.cancelMantikPod(pod, reason)
      }
    }
  }

  private def isOldImageNotFound(borderTime: Instant, pod: Pod): Boolean = {
    def containerIsMissingImage(container: skuber.Container.Status): Boolean = {
      !container.ready && (container.state match {
        case Some(w: skuber.Container.Waiting) if w.reason.contains("ImagePullBackOff") => true
        case _ => false
      })
    }

    def reachedTimeout(startTime: skuber.Timestamp): Boolean = {
      startTime.toInstant.isBefore(borderTime)
    }

    pod.status.exists { status =>
      val isPending = status.phase.contains(Pod.Phase.Pending)
      val isMissingContainerImage = status.containerStatuses.exists(containerIsMissingImage)
      val isReachedTimeout = status.startTime.exists(reachedTimeout)
      logger.debug(s"${pod.name} pending=${isPending} isMissingContainerImage=${isMissingContainerImage} isReachedTimeout=${isReachedTimeout}")
      isPending && isMissingContainerImage && isReachedTimeout
    }
  }

  def shutdown(): Unit = {
    checkPodCancellation.cancel()
  }
}

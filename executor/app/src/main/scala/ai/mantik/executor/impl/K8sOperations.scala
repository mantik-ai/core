package ai.mantik.executor.impl

import java.time.Clock
import java.time.temporal.ChronoUnit

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import ai.mantik.executor.Config
import ai.mantik.executor.Errors.{ InternalException, NotFoundException }
import play.api.libs.json.{ Format, JsObject }
import skuber.{ K8SException, LabelSelector, ListResource, Namespace, ObjectResource, Pod, ResourceDefinition }
import skuber.api.client.{ KubernetesClient, Status, WatchEvent }
import skuber.batch.Job

import scala.concurrent.{ ExecutionContext, Future, Promise, TimeoutException }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import skuber.json.format._
import skuber.json.batch.format._

/** Wraps Kubernetes Operations */
class K8sOperations(config: Config, rootClient: KubernetesClient)(implicit ex: ExecutionContext, actorSystem: ActorSystem, clock: Clock) {

  val logger = Logger(getClass)

  private def namespacedClient(ns: Option[String]): KubernetesClient = {
    ns.map(rootClient.usingNamespace).getOrElse(rootClient)
  }

  /** Start Multiple Pods and return their IP Address. */
  def startPodsAndGetIpAdresses(namespace: Option[String] = None, pods: Seq[Pod]): Future[Map[String, String]] = {
    Future.sequence {
      pods.map { pod =>
        startPodAndGetIpAddress(namespace, pod).map { ip =>
          pod.name -> ip
        }
      }
    }.map(_.toMap)
  }

  /** Start a Pod and return it's IP Address. */
  def startPodAndGetIpAddress(namespace: Option[String] = None, pod: Pod): Future[String] = {
    val startTime = clock.instant()
    create(namespace, pod).flatMap { result =>
      val ip = result.status.flatMap(_.podIP)
      ip match {
        case Some(ip) =>
          logger.info(s"Get IP in direct response")
          Future.successful(ip)
        case None =>
          val getPodAddressResult = getPodAddress(namespace, result.name)
          getPodAddressResult.foreach { ip =>
            val endTime = clock.instant()
            logger.info(s"Got IP Address of new pod ${result.name} ${ip} within ${endTime.toEpochMilli - startTime.toEpochMilli}ms")
          }
          getPodAddressResult
      }
    }
  }

  /** Create an object in Kubernetes. */
  def create[O <: ObjectResource](namespace: Option[String], obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
    errorHandling {
      namespacedClient(namespace).create(obj)
    }
  }

  /** Delete an object in Kubernetes. */
  def delete[O <: ObjectResource](namespace: Option[String], name: String, gracePeriodSeconds: Int = -1)(implicit rd: ResourceDefinition[O]): Future[Unit] = {
    errorHandling {
      namespacedClient(namespace).delete(name, gracePeriodSeconds)
    }
  }

  /** Watch an Object. */
  def watch[O <: ObjectResource](namespace: Option[String], obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Source[WatchEvent[O], _]] = {
    errorHandling {
      namespacedClient(namespace).watch(obj)
    }
  }

  /** Tries to get a Pod address. */
  def getPodAddress(namespace: Option[String] = None, name: String): Future[String] = {
    def tryFunction(): Future[Option[String]] = {
      namespacedClient(namespace).get[Pod](name).map { pod =>
        val ip = for {
          status <- pod.status
          ip <- status.podIP
        } yield ip
        ip
      }
    }

    tryMultipleTimes(config.defaultTimeout, config.defaultRetryInterval)(tryFunction())
  }

  /** Ensure the existence of a namespace, and returns a kubernetes client for this namespace. */
  def ensureNamespace(namespace: String): Future[Namespace] = {
    errorHandling(rootClient.get[Namespace](namespace)).map { ns =>
      logger.info(s"Using existing namespace ${namespace}")
      ns
    }.recoverWith {
      case e: K8SException if e.status.code.contains(404) =>
        logger.info(s"Namespace ${namespace} doesn't exist, trying to create")
        errorHandling(rootClient.create(Namespace(namespace))).andThen {
          case Success(_)     => logger.info(s"Namespace ${namespace} on the fly created")
          case Failure(error) => logger.error(s"Creating namespace ${namespace} failed", error)
        }
    }
  }

  /** Try `f` multiple times within a given timeout. */
  private def tryMultipleTimes[T](timeout: FiniteDuration, tryAgainWaitDuration: FiniteDuration)(f: => Future[Option[T]]): Future[T] = {
    val result = Promise[T]
    val finalTimeout = clock.instant().plus(timeout.toMillis, ChronoUnit.MILLIS)
    def tryAgain(): Unit = {
      f.andThen {
        case Success(None) =>
          if (clock.instant().isAfter(finalTimeout)) {
            result.tryFailure(new TimeoutException(s"Timeout after ${timeout}"))
          } else {
            actorSystem.scheduler.scheduleOnce(tryAgainWaitDuration)(tryAgain())
          }
        case Success(Some(x)) =>
          result.trySuccess(x)
        case Failure(e) =>
          result.tryFailure(e)
      }
    }
    tryAgain()
    result.future
  }

  /** Returns all Pods which are managed by this executor and which are in pending state. */
  def getAllManagedPendingPods(): Future[Map[String, List[Pod]]] = {
    errorHandling(rootClient.getNamespaceNames).flatMap { namespaces =>
      val futures = namespaces.map { namespace =>
        getPendingPods(Some(namespace)).map { result =>
          namespace -> result
        }
      }
      Future.sequence(futures).map(_.toMap)
    }
  }

  /** Returns pending pods managed by this executor. .*/
  private def getPendingPods(namespace: Option[String] = None): Future[List[Pod]] = {
    errorHandling {
      namespacedClient(namespace).listWithOptions[ListResource[skuber.Pod]](
        skuber.ListOptions(
          labelSelector = Some(LabelSelector(
            LabelSelector.IsEqualRequirement(KubernetesJobConverter.TrackerIdLabel, config.podTrackerId)
          )),
          fieldSelector = Some("status.phase==Pending")
        )
      ).map(_.items)
    }
  }

  /** Figures out a job by its id. */
  def getPodsByJobId(namespace: Option[String] = None, jobId: String): Future[List[Pod]] = {
    errorHandling {
      namespacedClient(namespace).listWithOptions[ListResource[Pod]](
        skuber.ListOptions(
          labelSelector = Some(LabelSelector(
            LabelSelector.IsEqualRequirement(KubernetesJobConverter.JobIdLabel, jobId)
          ))
        )
      ).map(_.items)
    }
  }

  /** Finds a Job in Kubernetes. */
  def getJob(namespace: Option[String] = None, jobId: String): Future[Option[Job]] = {
    errorHandling {
      namespacedClient(namespace).listWithOptions[ListResource[Job]] {
        skuber.ListOptions(
          labelSelector = Some(LabelSelector(
            LabelSelector.IsEqualRequirement(KubernetesJobConverter.JobIdLabel, jobId)
          ))
        )
      }.map {
        case x if x.isEmpty => None
        case x if x.length > 1 =>
          logger.warn(s"Multiple instances of jobs for id ${jobId} found")
          x.items.headOption
        case x => x.items.headOption
      }
    }
  }

  def getManagedNonFinishedJobsForAllNamespaces(): Future[Seq[Job]] = {
    errorHandling(rootClient.getNamespaceNames).flatMap { namespaces =>
      val futures = namespaces.map { namespace =>
        val jobs = getManagedNonFinishedJobs(Some(namespace))
        jobs
      }
      Future.sequence(futures).map(_.flatten)
    }
  }

  def getManagedNonFinishedJobs(namespace: Option[String] = None): Future[List[Job]] = {
    errorHandling {
      namespacedClient(namespace).listWithOptions[ListResource[Job]](
        skuber.ListOptions(
          labelSelector = Some(LabelSelector(
            LabelSelector.IsEqualRequirement(KubernetesJobConverter.TrackerIdLabel, config.podTrackerId)
          ))
        )
      ).map { jobs =>
          // Unfortunately there seem no way to filter for non-finished jobs directly, so we filter afterwards.
          // at least not with Field Selectors (Error: field label "status.succeeded" not supported for batchv1.Job )
          // This can become a bottleneck

          jobs.items.filter { job =>
            // Status/Condition/Type seems not acessable, however we can look for failed/successful runs
            // as we only use jobs with one run.
            val succeeded = job.status.flatMap(_.succeeded)
            val failed = job.status.flatMap(_.failed)
            succeeded.forall(_ == 0) && failed.forall(_ == 0)
          }
        }
    }
  }

  def getJobById(namespace: Option[String] = None, jobId: String): Future[Option[Job]] = {
    errorHandling {
      namespacedClient(namespace).listSelected[ListResource[skuber.batch.Job]](
        LabelSelector(
          LabelSelector.IsEqualRequirement(KubernetesJobConverter.JobIdLabel, jobId)
        )
      ).map { jobs =>
          if (jobs.items.length > 1) {
            throw new InternalException(s"Job ${jobId} found multiple times (${jobs.items.size} in isolation space ${namespace}")
          }
          jobs.items.headOption
        }
    }
  }

  def getJobLog(namespace: Option[String] = None, id: String): Future[String] = {
    getCoordinatorPod(namespace, id).flatMap {
      case None => Future.failed(new RuntimeException("Pod not found"))
      case Some(pod) =>
        errorHandling {
          rootClient.getPodLogSource(pod.name, Pod.LogQueryParams(), namespace).flatMap { source =>
            val collector = Sink.seq[ByteString]
            implicit val mat = ActorMaterializer()
            source.runWith(collector).map { byteStrings =>
              val combined = byteStrings.reduce(_ ++ _)
              combined.utf8String
            }
          }
        }
    }
  }

  private def getCoordinatorPod(namespace: Option[String] = None, jobId: String): Future[Option[Pod]] = {
    errorHandling {
      val client = namespacedClient(namespace)
      client.listWithOptions[ListResource[Pod]](
        skuber.ListOptions(
          labelSelector = Some(LabelSelector(
            LabelSelector.IsEqualRequirement(KubernetesJobConverter.JobIdLabel, jobId),
            LabelSelector.IsEqualRequirement(KubernetesJobConverter.RoleName, KubernetesJobConverter.CoordinatorRole)
          ))
        )
      ).map {
          case x if x.length > 1 =>
            throw new InternalException(s"Found multiple coordinator pods for job  ${jobId} in namespacespace ${namespace}")
          case otherwise =>
            otherwise.headOption
        }
    }
  }

  /** Cancels a Mantik Pod. */
  def cancelMantikPod(pod: Pod, reason: String): Future[Unit] = {
    val containerNames = for {
      status <- pod.status.toSeq
      ct <- status.containerStatuses
      if ct.name == KubernetesJobConverter.SidecarContainerName || ct.name == KubernetesJobConverter.CoordinatorContainerName
      if ct.state.exists(_.id == "running")
    } yield ct.name

    val containerName = containerNames match {
      case x if x.isEmpty =>
        logger.warn(s"Cannot cancel container ${pod.namespace}/${pod.name} as containers cannot be recognized...")
        return Future.failed(
          new RuntimeException(s"Pod ${pod.namespace}/${pod.name} is not a running mantik pod")
        )
      case x => x.head
    }

    logger.info(s"Issuing cancellation of ${pod.namespace}/${pod.name}")

    // Looks like skuber needs this sink
    val outputSink: Sink[String, Future[Done]] = Sink.foreach { s =>
      logger.debug(s"Stdout of Cancel call: ${s}")
    }
    val stdErrSink: Sink[String, Future[Done]] = Sink.foreach { s =>
      logger.debug(s"Stderr of Cancel Call: ${s}")
    }

    errorHandling {
      rootClient.usingNamespace(pod.namespace).exec(
        // This is a bid hardcoded..
        pod.name, command = List("/opt/bin/run", "cancel", "-reason", reason), maybeContainerName = Some(containerName), /*maybeStdout = Some(outputSink),*/ maybeStderr = Some(stdErrSink)
      )
    }
  }

  /** Some principal k8s Error handling. */
  def errorHandling[T](f: => Future[T]): Future[T] = {
    // For retry handling see: https://kubernetes.io/docs/reference/federation/v1/definitions/
    // And skuber.json.format.apiobj.statusReads

    def retryAfterSeconds(status: Status): Option[Int] = {
      status.details.flatMap {
        case s: JsObject =>
          s.value.get("retryAfterSeconds").flatMap { value =>
            value.asOpt[Int]
          }
        case _ => None
      }
    }

    val response = Promise[T]

    def tryAgain(remaining: Int): Unit = {
      f.andThen {
        case Success(value) =>
          response.trySuccess(value)
        case Failure(e) if remaining <= 0 =>
          logger.warn(s"K8S Operation failed, tried ${config.kubernetesRetryTimes}")
          response.tryFailure(e)
        case Failure(s: K8SException) if s.status.code.contains(500) && retryAfterSeconds(s.status).nonEmpty =>
          val retryValue = retryAfterSeconds(s.status).get
          val seconds = Math.min(Math.max(1, retryValue), config.defaultTimeout.toSeconds)
          logger.warn(s"K8S Operation failed with 500+retry ${retryValue}, will try again in ${seconds} seconds.")
          actorSystem.scheduler.scheduleOnce(seconds.seconds) {
            tryAgain(remaining - 1)
          }
        case Failure(e) =>
          response.tryFailure(e)
      }
    }

    tryAgain(config.kubernetesRetryTimes)
    response.future
  }
}

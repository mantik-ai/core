package ai.mantik.executor.impl

import java.time.Clock
import java.time.temporal.ChronoUnit

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import ai.mantik.executor.Config
import skuber.{ K8SException, LabelSelector, ListResource, Namespace, Pod }
import skuber.api.client.KubernetesClient
import skuber.batch.Job

import scala.concurrent.{ ExecutionContext, Future, Promise, TimeoutException }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import skuber.json.format._
import skuber.json.batch.format._

/** Some Higher level Kubernetes Operations. */
class K8sOperations(clock: Clock, config: Config)(implicit ex: ExecutionContext, actorSystem: ActorSystem) {

  val logger = Logger(getClass)

  /** Start Multiple Pods and return their IP Address. */
  def startPodsAndGetIpAdresses(kubernetesClient: KubernetesClient, pods: Seq[Pod]): Future[Map[String, String]] = {
    Future.sequence {
      pods.map { pod =>
        startPodAndGetIpAddress(kubernetesClient, pod).map { ip =>
          pod.name -> ip
        }
      }
    }.map(_.toMap)
  }

  /** Start a Pod and return it's IP Address. */
  def startPodAndGetIpAddress(kubernetesClient: KubernetesClient, pod: Pod): Future[String] = {
    val startTime = clock.instant()
    kubernetesClient.create(pod).flatMap { result =>
      val ip = result.status.flatMap(_.podIP)
      ip match {
        case Some(ip) =>
          logger.info(s"Get IP in direct response")
          Future.successful(ip)
        case None =>
          val getPodAddressResult = getPodAddress(kubernetesClient, result.name)
          getPodAddressResult.foreach { ip =>
            val endTime = clock.instant()
            logger.info(s"Got IP Address of new pod ${result.name} ${ip} within ${endTime.toEpochMilli - startTime.toEpochMilli}ms")
          }
          getPodAddressResult
      }
    }
  }

  /** Tries to get a Pod address. */
  def getPodAddress(kubernetesClient: KubernetesClient, name: String): Future[String] = {
    def tryFunction(): Future[Option[String]] = {
      kubernetesClient.get[Pod](name).map { pod =>
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
  def ensureNamespace(kubernetesClient: KubernetesClient, namespace: String): Future[KubernetesClient] = {
    kubernetesClient.get[Namespace](namespace).map { ns =>
      logger.info(s"Using existing namespace ${namespace}")
      kubernetesClient.usingNamespace(namespace)
    }.recoverWith {
      case e: K8SException if e.status.code.contains(404) =>
        logger.info(s"Namespace ${namespace} doesn't exist, trying to create")
        kubernetesClient.create(
          Namespace(namespace)
        ).map { _ =>
            logger.info(s"Namespace ${namespace} on the fly created")
            kubernetesClient.usingNamespace(namespace)
          }
    }
  }

  /** Try `f` multiple times within a given timeout. */
  private def tryMultipleTimes[T](timeout: FiniteDuration, tryAgainWaitDuration: FiniteDuration)(f: => Future[Option[T]]): Future[T] = {
    val result = Promise[T]
    val finalTimeout = clock.instant().plus(config.defaultTimeout.toMillis, ChronoUnit.MILLIS)
    def tryAgain(): Unit = {
      f.andThen {
        case Success(None) =>
          if (clock.instant().isAfter(finalTimeout)) {
            result.tryFailure(new TimeoutException(s"Timeout after ${timeout}"))
          } else {
            actorSystem.scheduler.scheduleOnce(config.defaultRetryInterval)(tryAgain())
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
  def getAllManagedPendingPods(client: KubernetesClient): Future[Map[String, ListResource[Pod]]] = {
    client.getNamespaceNames.flatMap { namespaces =>
      val futures = namespaces.map { namespace =>
        getPendingPods(client.usingNamespace(namespace)).map { result =>
          namespace -> result
        }
      }
      Future.sequence(futures).map(_.toMap)
    }
  }

  /** Returns pending pods managed by this executor. .*/
  private def getPendingPods(client: KubernetesClient): Future[ListResource[Pod]] = {
    client.listWithOptions[ListResource[skuber.Pod]](
      skuber.ListOptions(
        labelSelector = Some(LabelSelector(
          LabelSelector.IsEqualRequirement("trackerId", config.podTrackerId)
        )),
        fieldSelector = Some("status.phase==Pending")
      )
    )
  }

  /** Figures out a job by its id. */
  def getPodsByJobId(client: KubernetesClient, jobId: String): Future[ListResource[Pod]] = {
    client.listWithOptions[ListResource[Pod]](
      skuber.ListOptions(
        labelSelector = Some(LabelSelector(
          LabelSelector.IsEqualRequirement(KubernetesJobConverter.JobIdLabel, jobId)
        ))
      )
    )
  }

  /** Finds a Job in Kubernetes. */
  def getJob(client: KubernetesClient, jobId: String): Future[Option[Job]] = {
    client.listWithOptions[ListResource[Job]] {
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

  def getManagedJobsForAllNamespaces(client: KubernetesClient): Future[Seq[Job]] = {
    client.getNamespaceNames.flatMap { namespaces =>
      val futures = namespaces.map { namespace =>
        val jobs = getManagedJobs(client.usingNamespace(namespace))
        jobs
      }
      Future.sequence(futures).map(_.flatten)
    }
  }

  def getManagedJobs(client: KubernetesClient): Future[ListResource[Job]] = {
    client.listWithOptions[ListResource[Job]](
      skuber.ListOptions(
        labelSelector = Some(LabelSelector(
          LabelSelector.IsEqualRequirement(KubernetesJobConverter.TrackerIdLabel, config.podTrackerId)
        ))
      )
    )
  }

  def getJobLog(client: KubernetesClient, id: String): Future[String] = {
    client.listWithOptions[ListResource[Pod]](
      skuber.ListOptions(
        labelSelector = Some(LabelSelector(
          LabelSelector.IsEqualRequirement(KubernetesJobConverter.JobIdLabel, id),
          LabelSelector.IsEqualRequirement(KubernetesJobConverter.RoleName, KubernetesJobConverter.CoordinatorRole)
        ))
      )
    ).flatMap { pods =>
        pods.headOption match {
          case None => Future.failed(new RuntimeException("Pod not found"))
          case Some(pod) =>
            client.getPodLogSource(pod.name, Pod.LogQueryParams()).flatMap { source =>
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

  /** Cancels a Mantik Pod. */
  def cancelMantikPod(client: KubernetesClient, pod: Pod, reason: String): Future[Unit] = {
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

    client.usingNamespace(pod.namespace).exec(
      // This is a bid hardcoded..
      pod.name, command = List("/opt/bin/run", "cancel", "-reason", reason), maybeContainerName = Some(containerName), /*maybeStdout = Some(outputSink),*/ maybeStderr = Some(stdErrSink)
    )
  }
}

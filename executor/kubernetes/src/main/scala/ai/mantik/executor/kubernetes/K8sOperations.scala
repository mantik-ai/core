package ai.mantik.executor.kubernetes

import java.time.Clock
import java.time.temporal.ChronoUnit

import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.Errors.InternalException
import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import play.api.libs.json.{ Format, JsObject, JsValue, Json, Writes }
import skuber.Container.Running
import skuber.api.client.{ KubernetesClient, Status, WatchEvent }
import skuber.api.patch.{ JsonMergePatch, MetadataPatch }
import skuber.apps.Deployment
import skuber.apps.v1.ReplicaSet
import skuber.batch.Job
import skuber.ext.Ingress
import skuber.json.batch.format._
import skuber.json.ext.format._
import skuber.json.format._
import skuber.{ Container, K8SException, KListItem, LabelSelector, ListResource, Namespace, ObjectResource, Pod, ResourceDefinition, Secret, Service }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise, TimeoutException }
import scala.util.{ Failure, Success }

/** Wraps Kubernetes Operations */
class K8sOperations(config: Config, rootClient: KubernetesClient)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {

  // this seems to be missing in skuber
  private implicit val rsListFormat = skuber.json.format.ListResourceFormat[ReplicaSet]

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

  /**
   * Figures out the name/ip of Kubernetes we are talking to.
   * (For ingress interpolation)
   */
  def clusterServer: Uri = {
    Uri(rootClient.clusterServer)
  }

  /** Start a Pod and return it's IP Address. */
  def startPodAndGetIpAddress(namespace: Option[String] = None, pod: Pod): Future[String] = {
    val startTime = clock.instant()
    create(namespace, pod).flatMap { result =>
      val ip = result.status.flatMap(_.podIP)
      ip match {
        case Some(ip) =>
          logger.debug(s"Get IP in direct response")
          Future.successful(ip)
        case None =>
          val getPodAddressResult = getPodAddress(namespace, result.name)
          getPodAddressResult.foreach { ip =>
            val endTime = clock.instant()
            logger.debug(s"Got IP Address of new pod ${result.name} ${ip} within ${endTime.toEpochMilli - startTime.toEpochMilli}ms")
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

  /** Like create, but ignores argument if it's None. */
  def maybeCreate[O <: ObjectResource](namespace: Option[String], maybeObj: Option[O])(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Option[O]] = {
    maybeObj.map { obj =>
      create(namespace, obj).map {
        Some(_)
      }
    }.getOrElse(
      Future.successful(None)
    )
  }

  /** Creates or replaces  */
  def createOrReplace[O <: ObjectResource](namespace: Option[String], obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
    val client = namespacedClient(namespace)
    errorHandling(client.create(obj)).recoverWith {
      case e: K8SException if e.status.code.contains(409) =>
        // We could also try to use the Patch API, but this is very tricky to do it in a generic way
        logger.info(s"${obj.kind} ${obj.name} already exists, deleting...")
        for {
          _ <- errorHandling(client.delete[O](obj.name))
          _ = logger.debug(s"Waiting for deletion of ${obj.kind} ${obj.name}")
          _ <- waitUntilDeleted[O](namespace, obj.name)
          _ = logger.info(s"Recreating ${obj.kind} ${obj.name}")
          r <- errorHandling(client.create(obj))
        } yield r
    }
  }

  private def waitUntilDeleted[O <: ObjectResource](namespace: Option[String], name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Unit] = {
    val client = namespacedClient(namespace)
    def tryFunction(): Future[Option[Boolean]] = {
      errorHandling {
        client.getOption[O](name).map {
          case None    => Some(true)
          case Some(_) => None
        }
      }
    }
    FutureHelper.tryMultipleTimes(config.kubernetes.defaultTimeout, config.kubernetes.defaultRetryInterval)(tryFunction()).map { _ =>
      ()
    }
  }

  def deploymentSetReplicas(namespace: Option[String], deploymentName: String, replicas: Int): Future[Deployment] = {
    val jsonRequest = Json.obj(
      "replicas" -> replicas
    )
    import skuber.json.apps.format.depFormat
    jsonPatch[Deployment](namespace, deploymentName, jsonRequest)
  }

  def jsonPatch[O <: ObjectResource](namespace: Option[String], name: String, jsonRequest: JsValue)(
    implicit
    fmt: Format[O],
    rd: ResourceDefinition[O]
  ): Future[O] = {
    case object data extends JsonMergePatch
    implicit val writes: Writes[data.type] = Writes { _ =>
      Json.obj("spec" -> jsonRequest)
    }
    val client = namespacedClient(namespace)
    errorHandling {
      client.patch[data.type, O](name, data)
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

    FutureHelper.tryMultipleTimes(config.kubernetes.defaultTimeout, config.kubernetes.defaultRetryInterval)(tryFunction())
  }

  /** Ensure the existence of a namespace, and returns a kubernetes client for this namespace. */
  def ensureNamespace(namespace: String): Future[Namespace] = {
    errorHandling(rootClient.get[Namespace](namespace)).map { ns =>
      logger.trace(s"Using existing namespace ${namespace}, no creation necessary")
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

  /** Returns the namespace if available. */
  def getNamespace(namespace: String): Future[Option[Namespace]] = {
    errorHandling(rootClient.getOption[Namespace](namespace))
  }

  /** Returns all Pods which are managed by this executor and which are in pending state. */
  def getAllManagedPendingPods(): Future[Map[String, List[Pod]]] = {
    errorHandling(getManagedNamespaces).flatMap { namespaces =>
      val futures = namespaces.map { namespace =>
        getPendingPods(Some(namespace)).map { result =>
          namespace -> result
        }
      }
      Future.sequence(futures).map(_.toMap)
    }
  }

  /** Return namespace names, where Mantik Managed state can live. */
  private def getManagedNamespaces(): Future[List[String]] = {
    val prefix = config.kubernetes.namespacePrefix
    rootClient.getNamespaceNames.map { namespaceNames =>
      namespaceNames.filter(_.startsWith(prefix))
    }
  }

  /** Returns pending pods managed by this executor. .*/
  private def getPendingPods(namespace: Option[String] = None): Future[List[Pod]] = {
    errorHandling {
      namespacedClient(namespace).listWithOptions[ListResource[skuber.Pod]](
        skuber.ListOptions(
          labelSelector = Some(LabelSelector(
            LabelSelector.IsEqualRequirement(KubernetesConstants.ManagedLabel, KubernetesConstants.ManagedValue),
            LabelSelector.IsEqualRequirement(KubernetesConstants.TrackerIdLabel, KubernetesNamer.encodeLabelValue(config.podTrackerId))
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
            LabelSelector.IsEqualRequirement(KubernetesConstants.ManagedLabel, KubernetesConstants.ManagedValue),
            LabelSelector.IsEqualRequirement(KubernetesConstants.JobIdLabel, KubernetesNamer.encodeLabelValue(jobId))
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
            LabelSelector.IsEqualRequirement(KubernetesConstants.ManagedLabel, KubernetesConstants.ManagedValue),
            LabelSelector.IsEqualRequirement(KubernetesConstants.JobIdLabel, KubernetesNamer.encodeLabelValue(jobId))
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

  /** Looks for services. */
  def getServices(namespace: Option[String], serviceIdFilter: Option[String]): Future[List[Service]] = {
    errorHandling {
      namespacedClient(namespace).listSelected[ListResource[Service]] {
        encodeServiceLabelSelector(serviceIdFilter)
      }.map(_.items)
    }
  }

  def listSelected[T <: KListItem](namespace: Option[String], labelFilter: Seq[(String, String)])(
    implicit
    format: Format[ListResource[T]], rd: ResourceDefinition[ListResource[T]]
  ): Future[ListResource[T]] = {
    val labelSelector = LabelSelector(
      (labelFilter.map {
        case (k, v) =>
          LabelSelector.IsEqualRequirement(k, KubernetesNamer.encodeLabelValue(v))
      }.toVector): _*
    )
    errorHandling {
      namespacedClient(namespace).listSelected[ListResource[T]](
        labelSelector
      )
    }
  }

  def byName[T <: ObjectResource](namespace: Option[String], name: String)(
    implicit
    format: Format[T], resourceDefinition: ResourceDefinition[T]
  ): Future[Option[T]] = {
    errorHandling {
      namespacedClient(namespace).getOption[T](name)
    }
  }

  /** Delete deployed services and related stuff (Services, ReplicaSets, Secrets). */
  def deleteDeployedServicesAndRelated(namespace: Option[String], serviceIdFilter: Option[String]): Future[Int] = {
    errorHandling {
      val c = namespacedClient(namespace)
      val selector = encodeServiceLabelSelector(serviceIdFilter)
      val serviceDeletion = c.listSelected[ListResource[Service]](selector).flatMap { services =>
        if (services.items.length == 0) {
          logger.debug(s"Found no services matching filter ${serviceIdFilter} in namespace ${namespace}")
        }
        Future.sequence(services.map { element =>
          logger.debug(s"Deleting service ${element.name}")
          c.delete[Service](element.name)
        }).map { _ => services }
      }
      // This fails with 405 somehow ?!
      // val serviceDeletion = c.deleteAllSelected[ListResource[Service]](selector)
      val replicaSetDeletion = c.deleteAllSelected[ListResource[ReplicaSet]](selector)
      val secretDeletion = c.deleteAllSelected[ListResource[Secret]](selector)
      val ingressDeletion = c.deleteAllSelected[ListResource[Ingress]](selector)
      for {
        services <- serviceDeletion
        replicas <- replicaSetDeletion
        secrets <- secretDeletion
        ingresses <- ingressDeletion
      } yield {
        val deletedServices = services.items.length
        val deletedReplicaSets = replicas.items.length
        if (deletedServices != deletedReplicaSets) {
          logger.warn(s"Deleted Replica Set Count ${deletedReplicaSets} was != Deleted Service Result ${deletedServices}. Orphaned items?")
        }
        logger.debug(s"Deleted ${deletedServices} services (ingress=${ingresses.size}, secrets=${secrets.items.length}) in ${namespace} serviceIdFilter=${serviceIdFilter}")

        // do not warn for secrets, some deployed services do not have them...
        services.items.size
      }
    }
  }

  private def encodeServiceLabelSelector(serviceIdFilter: Option[String]): skuber.LabelSelector = {
    buildLabelSelector(
      KubernetesConstants.ManagedLabel -> Some(KubernetesConstants.ManagedValue),
      KubernetesConstants.ServiceIdLabel -> serviceIdFilter
    )
  }

  private def buildLabelSelector(values: (String, Option[String])*): skuber.LabelSelector = {
    LabelSelector(
      values.collect {
        case (key, Some(value)) => LabelSelector.IsEqualRequirement(key, KubernetesNamer.encodeLabelValue(value))
      }: _*
    )
  }

  def getManagedNonFinishedJobsForAllNamespaces(): Future[Seq[Job]] = {
    errorHandling(getManagedNamespaces).flatMap { namespaces =>
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
            LabelSelector.IsEqualRequirement(KubernetesConstants.ManagedLabel, KubernetesConstants.ManagedValue),
            LabelSelector.IsEqualRequirement(KubernetesConstants.TrackerIdLabel, config.podTrackerId)
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
          LabelSelector.IsEqualRequirement(KubernetesConstants.ManagedLabel, KubernetesConstants.ManagedValue),
          LabelSelector.IsEqualRequirement(KubernetesConstants.JobIdLabel, jobId)
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
          val killValue = pod.metadata.annotations.get(KubernetesConstants.KillAnnotationName)
          val killSuffix = killValue.map { value =>
            s"""
               |** Killed because of ${value} **""".stripMargin
          }.getOrElse("")
          rootClient.getPodLogSource(pod.name, Pod.LogQueryParams(), namespace).flatMap { source =>
            val collector = Sink.seq[ByteString]
            implicit val mat = ActorMaterializer()
            source.runWith(collector).map { byteStrings =>
              val combined = byteStrings.reduce(_ ++ _)
              combined.utf8String ++ killSuffix
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
            LabelSelector.IsEqualRequirement(KubernetesConstants.JobIdLabel, jobId),
            LabelSelector.IsEqualRequirement(KubernetesConstants.RoleName, KubernetesConstants.CoordinatorRole)
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

  /** Kills a Mantik Job. This works by removing the workers and cancelling the coordinators. */
  def killMantikJob(namespace: String, jobId: String, reason: String): Future[Unit] = {
    logger.info(s"Canceling Mantik job ${jobId}")

    def roleFiler(role: String): Pod => Boolean = {
      pod => pod.metadata.labels.get(KubernetesConstants.RoleName).contains(role)
    }

    for {
      job <- getJobById(Some(namespace), jobId)
      _ <- Future.sequence(job.toSeq.map { job => sendKillPatch[Job](job.name, Some(job.namespace), reason) })
      pods <- getPodsByJobId(Some(namespace), jobId)
      workers = pods.filter(roleFiler(KubernetesConstants.WorkerRole))
      coordinator = pods.filter(roleFiler(KubernetesConstants.CoordinatorRole))
      _ = logger.debug(s"Job ${jobId} has ${workers.length} workers and ${coordinator.length} coordinators")
      _ <- Future.sequence(coordinator.map(c => cancelMantikPod(c, reason)))
      _ <- Future.sequence(workers.map { w => delete[Pod](Some(w.namespace), w.name) })
    } yield {
      ()
    }
  }

  /** Cancels a Mantik Pod. */
  def cancelMantikPod(pod: Pod, reason: String): Future[Unit] = {
    for {
      _ <- sendKillPatch[Pod](pod.name, Some(pod.namespace), reason)
      cancelableContainers = filterRunningSidecarOrCoordinator(pod).map(_.name).toList
      _ = logger.info(s"Pod ${pod.name} cancellable containers: ${cancelableContainers}")
      _ <- Future.sequence(cancelableContainers.map { c => sendCancelSignal(pod, c, reason) })
    } yield {
      ()
    }
  }

  /**  Add Kill-Annotations to some items. */
  def sendKillPatch[T <: ObjectResource](name: String, namespace: Option[String], reason: String)(
    implicit
    fmt: Format[T], rd: ResourceDefinition[T]
  ): Future[Unit] = {
    val patch = MetadataPatch(annotations = Some(Map(
      KubernetesConstants.KillAnnotationName -> reason
    )))
    errorHandling {
      rootClient.patch[MetadataPatch, T](name, patch, namespace)
    }.map { _ => () }
  }

  private def sendCancelSignal(pod: Pod, containerName: String, reason: String): Future[Unit] = {
    val outputSink: Sink[String, Future[Done]] = Sink.foreach { s =>
      logger.debug(s"Stdout of Cancel call: ${s}")
    }
    val stdErrSink: Sink[String, Future[Done]] = Sink.foreach { s =>
      logger.debug(s"Stderr of Cancel Call: ${s}")
    }
    rootClient.usingNamespace(pod.namespace).exec(
      // This is a bid hardcoded..
      pod.name, command = List("/opt/bin/run", "cancel", "-reason", reason), maybeContainerName = Some(containerName), /*maybeStdout = Some(outputSink),*/ maybeStderr = Some(stdErrSink)
    )
  }

  /** Look for a running sidecar or coordinator inside a Mantik Pod */
  def filterRunningSidecarOrCoordinator(pod: Pod): Option[skuber.Container.Status] = {
    val result = for {
      status <- pod.status.toList
      containerStatus <- status.containerStatuses
      if containerStatus.name == KubernetesConstants.SidecarContainerName || containerStatus.name == KubernetesConstants.CoordinatorContainerName
      state <- containerStatus.state.toList
      if state.isInstanceOf[Running]
    } yield containerStatus
    result.headOption
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
          logger.warn(s"K8S Operation failed, tried ${config.kubernetes.retryTimes}")
          response.tryFailure(e)
        case Failure(s: K8SException) if s.status.code.contains(500) && retryAfterSeconds(s.status).nonEmpty =>
          val retryValue = retryAfterSeconds(s.status).get
          val seconds = Math.min(Math.max(1, retryValue), config.kubernetes.defaultTimeout.toSeconds)
          logger.warn(s"K8S Operation failed with 500+retry ${retryValue}, will try again in ${seconds} seconds.")
          actorSystem.scheduler.scheduleOnce(seconds.seconds) {
            tryAgain(remaining - 1)
          }
        case Failure(e) =>
          response.tryFailure(e)
      }
    }

    tryAgain(config.kubernetes.retryTimes)
    response.future
  }
}

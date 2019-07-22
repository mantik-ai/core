package ai.mantik.executor.kubernetes

import java.time.{ Clock, Instant }
import java.util.UUID

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.Errors.NotFoundException
import ai.mantik.executor.kubernetes.buildinfo.BuildInfo
import ai.mantik.executor.kubernetes.tracker.KubernetesTracker
import ai.mantik.executor.model._
import ai.mantik.executor.{ Errors, Executor }
import akka.actor.{ ActorSystem, Cancellable }
import com.google.common.net.InetAddresses
import javax.inject.{ Inject, Provider, Singleton }
import skuber.json.batch.format._
import skuber.json.ext.format._
import skuber.json.format._
import skuber.{ Endpoints, ObjectMeta, Pod, Service }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

/** Kubennetes implementation of [[Executor]]. */
class KubernetesExecutor(config: Config, ops: K8sOperations)(
    implicit
    akkaRuntime: AkkaRuntime
) extends ComponentBase with Executor {
  val tracker = new KubernetesTracker(config, ops)

  val kubernetesHost = ops.clusterServer.authority.host.address()
  logger.info(s"Initializing with kubernetes at address ${kubernetesHost}")
  logger.info(s"Docker Default Tag:  ${config.dockerConfig.defaultImageTag}")
  logger.info(s"Docker Default Repo: ${config.dockerConfig.defaultImageRepository}")
  logger.info(s"Node collapsing:     ${config.enableExistingServiceNodeCollapse}")
  logger.info(s"Disable Pull:        ${config.kubernetes.disablePull}")

  override def schedule(job: Job): Future[String] = {
    val jobId = UUID.randomUUID().toString
    val namespace = namespaceForIsolationSpace(job.isolationSpace)
    logger.info(s"Creating job ${jobId} in namespace ${namespace}...")

    val converter = try {
      new KubernetesJobConverter(config, job, jobId)
    } catch {
      case e: GraphAnalysis.AnalyzerException =>
        logger.warn(s"Graph analysis failed", e)
        return Future.failed(new Errors.BadRequestException(s"Graph analysis failed: e: ${e.getMessage}"))
    }

    val maybePullSecret = converter.pullSecret

    for {
      _ <- ops.ensureNamespace(namespace)
      _ <- ops.maybeCreate(Some(namespace), maybePullSecret)
      // Pods are not reachable by it's name but by their IP Address, however we must first start them to get their IP Address.
      podsWithIpAdresses <- ops.startPodsAndGetIpAdresses(Some(namespace), converter.pods)
      _ = logger.debug(s"Created pods for ${jobId}: ${podsWithIpAdresses.values}")
      configMap = converter.configuration(podsWithIpAdresses)
      _ <- ops.create(Some(namespace), configMap)
      _ = logger.debug(s"Created ConfigMap for ${jobId}")
      job = converter.convertCoordinator
      _ <- ops.create(Some(namespace), job)
    } yield {
      logger.info(s"Created ${podsWithIpAdresses.size} pods, config map and job ${job.name}")
      tracker.subscribe(job)
      jobId
    }
  }

  private def namespaceForIsolationSpace(isolationSpace: String): String = {
    // TODO: Escape invalid characters.
    config.kubernetes.namespacePrefix + isolationSpace
  }

  override def status(isolationSpace: String, id: String): Future[JobStatus] = {
    val namespace = namespaceForIsolationSpace(isolationSpace)
    ops.getJobById(Some(namespace), id).map {
      case None =>
        throw new NotFoundException(s"Job ${id} not found in isolationSpace ${isolationSpace}")
      case Some(job) =>
        job.status.map { status =>
          logger.trace(s"Decoding job state ${status}")
          status.active match {
            case Some(a) if a > 0 => JobStatus(JobState.Running)
            case _ =>
              status.failed match {
                case Some(x) if x > 0 => JobStatus(JobState.Failed)
                case _ =>
                  status.succeeded match {
                    case Some(x) if x > 0 => JobStatus(JobState.Finished)
                    case _ =>
                      logger.debug(s"Could not decode ${status}, probably pending")
                      JobStatus(JobState.Pending)
                  }
              }
          }
        }.getOrElse {
          logger.debug(s"No job state found for ${id} in ${namespace}, probably pending")
          JobStatus(
            state = JobState.Pending
          )
        }
    }
  }

  override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
    // IP Adresses need an endpoint, while DNS names can be done via ExternalName
    // (See https://kubernetes.io/docs/concepts/services-networking/service/#externalname )

    val isIpAddress = InetAddresses.isInetAddress(publishServiceRequest.externalName)

    if (!isIpAddress && publishServiceRequest.externalPort != publishServiceRequest.port) {
      return Future.failed(new Errors.BadRequestException("Can't bind a service name with a different port number to kubernetes"))
    }

    val namespace = namespaceForIsolationSpace(publishServiceRequest.isolationSpace)

    val service = Service(
      metadata = ObjectMeta(
        name = publishServiceRequest.serviceName,
        namespace = namespace
      ),
      spec = Some(Service.Spec(
        ports = List(
          Service.Port(
            port = publishServiceRequest.port,
            name = s"port${publishServiceRequest.port}" // create unique name
          )
        ),
        _type = if (isIpAddress) Service.Type.ClusterIP else Service.Type.ExternalName,
        externalName = if (isIpAddress) "" else publishServiceRequest.externalName
      ))
    )

    val endpoints = if (isIpAddress) Some(Endpoints(
      metadata = ObjectMeta(
        name = publishServiceRequest.serviceName,
        namespace = namespace
      ),
      subsets = List(
        Endpoints.Subset(
          addresses = List(
            Endpoints.Address(
              ip = publishServiceRequest.externalName
            )
          ),
          notReadyAddresses = None,
          ports = List(
            Endpoints.Port(
              port = publishServiceRequest.externalPort,
              name = Some(s"port${publishServiceRequest.port}")
            )
          )
        )
      )
    ))
    else None

    for {
      _ <- ops.ensureNamespace(namespace)
      service <- ops.createOrReplace(Some(namespace), service)
      _ <- endpoints.map(ops.createOrReplace(Some(namespace), _)).getOrElse(Future.successful(()))
    } yield {
      logger.info(s"Ensured service ${namespace}/${publishServiceRequest.serviceName}")
      val name = s"${service.name}.${service.namespace}.svc.cluster.local:${publishServiceRequest.port}"
      PublishServiceResponse(
        name
      )
    }
  }

  override def deployService(deployServiceRequest: DeployServiceRequest): Future[DeployServiceResponse] = {
    val namespace = namespaceForIsolationSpace(deployServiceRequest.isolationSpace)
    val converter = new KubernetesServiceConverter(config, deployServiceRequest, kubernetesHost)
    val maybePullSecret = converter.pullSecret
    val replicaSet = converter.replicaSet
    val service = converter.service

    for {
      _ <- ops.ensureNamespace(namespace)
      _ <- ops.maybeCreate(Some(namespace), maybePullSecret)
      _ <- ops.create(Some(namespace), replicaSet)
      usedService <- ops.create(Some(namespace), service)
      maybeIngress = converter.ingress(usedService.name)
      _ <- maybeIngress.map { ingress =>
        ops.createOrReplace(Some(namespace), ingress)
      }.getOrElse(Future.successful(()))
    } yield {
      val serviceUrl = s"http://${usedService.name}"
      val ingressUrl = converter.ingressExternalUrl
      logger.info(s"Deployed ${deployServiceRequest.serviceId} as ${service.name} under ${serviceUrl} in namespace ${namespace}, ingress=${ingressUrl}")
      DeployServiceResponse(
        serviceName = usedService.name,
        url = serviceUrl,
        externalUrl = ingressUrl
      )
    }
  }

  override def queryDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[DeployedServicesResponse] = {
    val namespace = namespaceForIsolationSpace(deployedServicesQuery.isolationSpace)
    ops.getNamespace(namespace).flatMap {
      case None =>
        logger.debug(s"Namespace ${namespace} doesn't exist, returning 0 services")
        Future.successful(DeployedServicesResponse(Nil))
      case Some(_) =>
        ops.getServices(
          Some(namespace),
          serviceIdFilter = deployedServicesQuery.serviceId
        ).map { services =>
            val entries = services.map { service =>
              val serviceId = KubernetesNamer.decodeLabelValueNoCrashing(
                service.metadata.labels.getOrElse(KubernetesConstants.ServiceIdLabel, {
                  logger.error(s"Found a service without service id: ${namespace}/${service.name}")
                  ""
                }
                ))
              DeployedServicesEntry(
                serviceId = serviceId,
                serviceUrl = s"http://${service.name}"
              )
            }
            DeployedServicesResponse(
              entries
            )
          }
    }
  }

  override def deleteDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[Int] = {
    val namespace = namespaceForIsolationSpace(deployedServicesQuery.isolationSpace)
    ops.getNamespace(namespace).flatMap {
      case None =>
        logger.debug(s"Namespace ${namespace} not found, no services deleted")
        Future.successful(0)
      case Some(_) =>
        ops.deleteDeployedServicesAndRelated(
          Some(namespace),
          serviceIdFilter = deployedServicesQuery.serviceId
        )
    }
  }

  override def logs(isolationSpace: String, id: String): Future[String] = {
    val namespace = namespaceForIsolationSpace(isolationSpace)
    ops.getJobLog(Some(namespace), id)
  }

  private val checkPodCancellation = config.kubernetes.checkPodInterval match {
    case f: FiniteDuration =>
      actorSystem.scheduler.schedule(f, f)(checkPods())
    case _ => // nothing
      Cancellable.alreadyCancelled
  }

  private def checkPods(): Unit = {
    logger.trace("Checking Pods")
    val timestamp = clock.instant()
    checkBrokenImagePods(timestamp)
  }

  private def checkBrokenImagePods(timestamp: Instant): Unit = {
    val borderTime = timestamp.minusSeconds(config.kubernetes.podPullImageTimeout.toSeconds)
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
    val maybeJobId = pod.metadata.labels.get(KubernetesConstants.JobIdLabel)
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
      logger.trace(s"${pod.name} pending=${isPending} isMissingContainerImage=${isMissingContainerImage} isReachedTimeout=${isReachedTimeout}")
      isPending && isMissingContainerImage && isReachedTimeout
    }
  }

  override def shutdown(): Unit = {
    checkPodCancellation.cancel()
  }

  override def nameAndVersion: String = s"KubernetesExecutor ${BuildInfo.version}  (${BuildInfo.gitVersion}-${BuildInfo.buildNum})"
}

class KubernetesExecutorProvider @Inject() (implicit akkaRuntime: AkkaRuntime) extends Provider[KubernetesExecutor] {

  override def get(): KubernetesExecutor = {
    import ai.mantik.componently.AkkaHelper._
    val config = Config.fromTypesafeConfig(akkaRuntime.config)
    val kubernetesClient = skuber.k8sInit
    val k8sOperations = new K8sOperations(config, kubernetesClient)
    val executor = new KubernetesExecutor(config, k8sOperations)
    executor
  }
}

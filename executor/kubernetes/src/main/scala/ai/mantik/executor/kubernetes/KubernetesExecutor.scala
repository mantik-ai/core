package ai.mantik.executor.kubernetes

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.kubernetes.buildinfo.BuildInfo
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.{Errors, Executor}
import akka.actor.Cancellable
import com.google.common.net.InetAddresses

import javax.inject.{Inject, Provider}
import play.api.libs.json.Json
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.{Endpoints, LabelSelector, ObjectMeta, Pod, Service}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/** Kubennetes implementation of [[Executor]]. */
class KubernetesExecutor(config: Config, ops: K8sOperations)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase
    with Executor {
  val kubernetesHost = ops.clusterServer.authority.host.address()
  logger.info(s"Initializing with kubernetes at address ${kubernetesHost}")
  logger.info(s"Docker Default Tag:  ${config.dockerConfig.defaultImageTag}")
  logger.info(s"Docker Default Repo: ${config.dockerConfig.defaultImageRepository}")
  logger.info(s"Disable Pull:        ${config.common.disablePull}")
  val nodeAddress = config.kubernetes.nodeAddress.getOrElse(kubernetesHost)
  logger.info(s"Node Address:        ${nodeAddress}")

  private def namespaceForIsolationSpace(isolationSpace: String): String = {
    // TODO: Escape invalid characters.
    config.kubernetes.namespacePrefix + isolationSpace
  }

  override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] =
    logErrors(s"PublishService ${publishServiceRequest.serviceName}") {
      // IP Adresses need an endpoint, while DNS names can be done via ExternalName
      // (See https://kubernetes.io/docs/concepts/services-networking/service/#externalname )

      val isIpAddress = InetAddresses.isInetAddress(publishServiceRequest.externalName)

      if (!isIpAddress && publishServiceRequest.externalPort != publishServiceRequest.port) {
        return Future.failed(
          new Errors.BadRequestException("Can't bind a service name with a different port number to kubernetes")
        )
      }

      val namespace = namespaceForIsolationSpace(publishServiceRequest.isolationSpace)

      val service = Service(
        metadata = ObjectMeta(
          name = publishServiceRequest.serviceName,
          namespace = namespace
        ),
        spec = Some(
          Service.Spec(
            ports = List(
              Service.Port(
                port = publishServiceRequest.port,
                name = s"port${publishServiceRequest.port}" // create unique name
              )
            ),
            _type = if (isIpAddress) Service.Type.ClusterIP else Service.Type.ExternalName,
            externalName = if (isIpAddress) "" else publishServiceRequest.externalName
          )
        )
      )

      val endpoints =
        if (isIpAddress)
          Some(
            Endpoints(
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
            )
          )
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

  private def checkBrokenImagePods(currentTime: Instant): Unit = {
    Workload.listPending(ops).foreach { pending =>
      val withImageError: Vector[(Workload, String)] = pending.flatMap { workload =>
        workload.hasBrokenImage(config, currentTime).map { brokenImage =>
          workload -> brokenImage
        }
      }

      withImageError.foreach { case (workload, imageError) =>
        logger.info(s"Stopping workload ${workload.internalId} because of image error ${imageError}")
        val error = s"Pod has image error ${imageError}"
        workload.stop(false, ops, Some(error))
      }
    }
  }

  addShutdownHook {
    checkPodCancellation.cancel()
    Future.successful(())
  }

  override def nameAndVersion: Future[String] = {
    val str = s"KubernetesExecutor ${BuildInfo.version}  (${BuildInfo.gitVersion}-${BuildInfo.buildNum})"
    Future.successful(str)
  }

  override def grpcProxy(isolationSpace: String): Future[GrpcProxy] = logErrors("GrpcProxy") {
    val grpcProxyConfig = config.common.grpcProxy
    if (!grpcProxyConfig.enabled) {
      return Future.failed(
        new Errors.NotFoundException("Grpc Proxy not enabled")
      )
    }
    val namespace = namespaceForIsolationSpace(isolationSpace)
    Option(grpcProxies.get(namespace)) match {
      case Some(alreadyExists) => return Future.successful(alreadyExists)
      case None                => // continue
    }

    val deployment = Deployment(
      metadata = ObjectMeta(
        name = grpcProxyConfig.containerName,
        labels = Map(
          LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
        )
      ),
      spec = Some(
        Deployment.Spec(
          selector = LabelSelector(
            LabelSelector.IsEqualRequirement(LabelConstants.ManagedByLabelName, LabelConstants.ManagedByLabelValue),
            LabelSelector.IsEqualRequirement(LabelConstants.RoleLabelName, LabelConstants.role.grpcProxy)
          ),
          template = Pod.Template.Spec(
            metadata = ObjectMeta(
              labels = Map(
                LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
                LabelConstants.RoleLabelName -> LabelConstants.role.grpcProxy
              )
            ),
            spec = Some(
              Pod.Spec(
                containers = List(
                  skuber.Container(
                    name = "main",
                    image = grpcProxyConfig.container.image,
                    args = grpcProxyConfig.container.parameters.toList,
                    imagePullPolicy = KubernetesConverter.createImagePullPolicy(
                      config.common.disablePull,
                      grpcProxyConfig.container
                    )
                  )
                )
              )
            )
          )
        )
      )
    )
    val service = Service(
      metadata = ObjectMeta(
        name = grpcProxyConfig.containerName,
        labels = Map(
          LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
        )
      ),
      spec = Some(
        Service.Spec(
          _type = Service.Type.NodePort,
          selector = Map(
            LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
            LabelConstants.RoleLabelName -> LabelConstants.role.grpcProxy
          ),
          ports = List(
            Service.Port(
              port = grpcProxyConfig.port,
              targetPort = Some(Left(grpcProxyConfig.port))
            )
          )
        )
      )
    )
    import skuber.json.apps.format.depFormat
    for {
      withNamespace <- ops.ensureNamespace(namespace)
      deploymentAssembled <- ops.createOrReplace(Some(namespace), deployment)
      serviceAssembled <- ops.createOrReplace(Some(namespace), service)
    } yield {
      val nodePort = serviceAssembled.spec.get.ports.head.nodePort
      val grpcProxy = GrpcProxy(
        proxyUrl = Some(s"http://${nodeAddress}:$nodePort")
      )
      logger.info(s"Ensured grpc Proxy ${grpcProxy.proxyUrl} for namespace ${namespace}")
      grpcProxies.put(namespace, grpcProxy)
      grpcProxy
    }
  }

  override def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse] =
    logErrors(s"StartWorker ${startWorkerRequest.id}") {
      val converter = KubernetesConverter(config, kubernetesHost)
      val internalId = UUID.randomUUID().toString
      val converted = converter.convertStartWorkRequest(internalId, startWorkerRequest)
      val namespace = namespaceForIsolationSpace(startWorkerRequest.isolationSpace)

      val t0 = System.currentTimeMillis()
      ops.ensureNamespace(namespace).flatMap { _ =>
        converted.create(Some(namespace), ops).map { created =>
          val t1 = System.currentTimeMillis()
          val ingressUrl = created.ingressUrl(config, kubernetesHost)
          logger.info(
            s"Created Worker ${created.service.name} within ${t1 - t0}ms (ingress=${ingressUrl}, userId=${startWorkerRequest.id}, internalId=${internalId})"
          )
          StartWorkerResponse(
            nodeName = created.service.name,
            externalUrl = ingressUrl
          )
        }
      }
    }

  override def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse] =
    logErrors(s"ListWorkers") {
      val namespace = namespaceForIsolationSpace(listWorkerRequest.isolationSpace)
      listWorkersImpl(namespace, listWorkerRequest.nameFilter, listWorkerRequest.idFilter).map { workloads =>
        ListWorkerResponse(
          workloads.map { workload =>
            generateListWorkerResponseElement(workload)
          }
        )
      }
    }

  private def listWorkersImpl(
      namespace: String,
      nameFilter: Option[String],
      userIdFilter: Option[String]
  ): Future[Vector[Workload]] = {
    val labels = Seq(
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
    ) ++ userIdFilter.map { id =>
      LabelConstants.UserIdLabelName -> KubernetesNamer.encodeLabelValue(id)
    }
    Workload.list(Some(namespace), nameFilter, labels, ops)
  }

  private def generateListWorkerResponseElement(workload: Workload): ListWorkerResponseElement = {
    val workerType = workload.service.metadata.labels.get(LabelConstants.WorkerTypeLabelName) match {
      case Some(LabelConstants.workerType.mnpWorker)   => WorkerType.MnpWorker
      case Some(LabelConstants.workerType.mnpPipeline) => WorkerType.MnpPipeline
      case other =>
        logger.warn(s"Unexpected worker type ${other}, assuming regular mnp worker")
        WorkerType.MnpWorker
    }
    val ingressUrl = workload.ingressUrl(config, kubernetesHost)
    ListWorkerResponseElement(
      nodeName = workload.service.name,
      id = KubernetesNamer.decodeLabelValue(
        workload.service.metadata.labels.getOrElse(LabelConstants.UserIdLabelName, "unknown")
      ),
      state = workload.workerState,
      container = {
        val maybeMainContainer = workload.pod
          .flatMap(_.spec.flatMap(_.containers.headOption))
          .orElse(
            workload.deployment.flatMap(_.spec.map(_.template).flatMap(_.spec).flatMap(_.containers.headOption))
          )
        maybeMainContainer.map { mainContainer =>
          Container(
            image = mainContainer.image,
            parameters = mainContainer.args
          )
        }
      },
      `type` = workerType,
      externalUrl = ingressUrl
    )
  }

  override def stopWorker(stopWorkerRequest: StopWorkerRequest): Future[StopWorkerResponse] = logErrors(s"StopWorker") {
    val namespace = namespaceForIsolationSpace(stopWorkerRequest.isolationSpace)
    listWorkersImpl(namespace, stopWorkerRequest.nameFilter, stopWorkerRequest.idFilter).flatMap { workloads =>
      val responses = workloads.map { workload =>
        StopWorkerResponseElement(
          id = KubernetesNamer.decodeLabelValue(
            workload.service.metadata.labels.getOrElse(LabelConstants.UserIdLabelName, "Unknown")
          ),
          name = workload.service.name
        )
      }

      val subFutures = workloads.map { workload =>
        workload.stop(stopWorkerRequest.remove.value, ops)
      }

      Future.sequence(subFutures).map { _ =>
        StopWorkerResponse(
          responses
        )
      }
    }
  }

  private def logErrors[T](what: String)(f: => Future[T]): Future[T] = {
    val t0 = System.currentTimeMillis()
    f.andThen {
      case Failure(exception) =>
        logger.warn(s"${what} failed", exception)
      case Success(value) =>
        val t1 = System.currentTimeMillis()
        logger.trace(s"${what} executed within ${t1 - t0}ms")
    }
  }

  /** Maps namespace to grpcProxy. */
  private val grpcProxies = new ConcurrentHashMap[String, GrpcProxy]()
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

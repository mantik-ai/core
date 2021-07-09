/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
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
  logger.info(s"Docker Default Tag:  ${config.dockerConfig.defaultImageTag.getOrElse("<empty>")}")
  logger.info(s"Docker Default Repo: ${config.dockerConfig.defaultImageRepository.getOrElse("<empty>")}")
  logger.info(s"Disable Pull:        ${config.common.disablePull}")
  val nodeAddress = config.kubernetes.nodeAddress.getOrElse(kubernetesHost)
  logger.info(s"Node Address:        ${nodeAddress}")
  val namespace = config.namespace
  logger.info(s"Namespace:           ${namespace}")

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

  override def grpcProxy(): Future[GrpcProxy] = {
    lazyGrpcProxy
  }

  private lazy val lazyGrpcProxy: Future[GrpcProxy] = ensureGrpcProxy()

  private def ensureGrpcProxy(): Future[GrpcProxy] = logErrors("GrpcProxy") {
    val grpcProxyConfig = config.common.grpcProxy
    if (!grpcProxyConfig.enabled) {
      logger.info(s"Grpc Proxy not enabled")
      return Future.successful(
        GrpcProxy(None)
      )
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
      grpcProxy
    }
  }

  override def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse] =
    logErrors(s"StartWorker ${startWorkerRequest.id}") {
      val converter = KubernetesConverter(config, kubernetesHost)
      val internalId = UUID.randomUUID().toString
      val converted = converter.convertStartWorkRequest(internalId, startWorkerRequest)

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
      listWorkersImpl(listWorkerRequest.nameFilter, listWorkerRequest.idFilter).map { workloads =>
        ListWorkerResponse(
          workloads.map { workload =>
            generateListWorkerResponseElement(workload)
          }
        )
      }
    }

  private def listWorkersImpl(
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
    listWorkersImpl(stopWorkerRequest.nameFilter, stopWorkerRequest.idFilter).flatMap { workloads =>
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

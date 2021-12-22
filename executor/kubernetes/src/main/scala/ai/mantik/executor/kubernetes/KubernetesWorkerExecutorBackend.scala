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

import ai.mantik.componently.utils.FutureHelper

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.common.workerexec.model.{
  ListWorkerRequest,
  ListWorkerResponse,
  ListWorkerResponseElement,
  StartWorkerRequest,
  StartWorkerResponse,
  StopWorkerRequest,
  StopWorkerResponse,
  StopWorkerResponseElement,
  WorkerState
}
import ai.mantik.executor.common.workerexec.{WorkerExecutorBackend, WorkerBasedExecutor, WorkerMetrics, model}
import ai.mantik.executor.common.{GrpcProxy, LabelConstants}
import ai.mantik.executor.kubernetes.buildinfo.BuildInfo
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.{Errors, Executor, PayloadProvider}
import ai.mantik.mnp.MnpClient
import akka.actor.Cancellable
import com.google.common.net.InetAddresses

import javax.inject.{Inject, Provider}
import play.api.libs.json.Json
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.{Endpoints, LabelSelector, Namespace, ObjectMeta, Pod, Service}

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/** Kubernetes implementation of [[WorkerExecutorBackend]]. */
class KubernetesWorkerExecutorBackend(config: Config, ops: K8sOperations)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase
    with WorkerExecutorBackend {
  val kubernetesHost = ops.clusterServer.authority.host.address()
  logger.info(s"Initializing with kubernetes at address ${kubernetesHost}")
  logger.info(s"Docker Default Tag:  ${config.dockerConfig.defaultImageTag.getOrElse("<empty>")}")
  logger.info(s"Docker Default Repo: ${config.dockerConfig.defaultImageRepository.getOrElse("<empty>")}")
  logger.info(s"Disable Pull:        ${config.common.disablePull}")
  val nodeAddress = config.kubernetes.nodeAddress.getOrElse(kubernetesHost)
  logger.info(s"Node Address:        ${nodeAddress}")
  val namespace = config.namespace
  logger.info(s"Namespace:           ${namespace}")

  private val checkPodCancellation = config.kubernetes.checkPodInterval match {
    case f: FiniteDuration =>
      actorSystem.scheduler.scheduleWithFixedDelay(f, f) { () =>
        checkPods()
      }
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

  // Ensure namespace on init
  private val namespaceFuture: Future[Namespace] = ops.ensureNamespace(namespace)
  private val grpcProxy: Future[GrpcProxy] = ensureGrpcProxy()

  private def ensureGrpcProxy(): Future[GrpcProxy] = logErrors("GrpcProxy") {
    val grpcProxyContainer = config.common.grpcProxyContainer
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
                    image = grpcProxyContainer.image,
                    args = grpcProxyContainer.parameters.toList,
                    imagePullPolicy = KubernetesConverter.createImagePullPolicy(
                      config.common.disablePull,
                      grpcProxyContainer
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
      withNamespace <- namespaceFuture
      deploymentAssembled <- ops.createOrReplace(Some(namespace), deployment)
      serviceAssembled <- ops.createOrReplace(Some(namespace), service)
      _ <- FutureHelper.asyncWait(grpcProxyConfig.startupTime)
    } yield {
      val nodePort = serviceAssembled.spec.get.ports.head.nodePort
      val grpcProxy = GrpcProxy(
        proxyUrl = Some(s"http://${nodeAddress}:$nodePort")
      )
      logger.info(s"Ensured grpc Proxy ${grpcProxy.proxyUrl} for namespace ${namespace}")
      grpcProxy
    }
  }

  override def connectMnp(address: String): Future[MnpClient] = {
    grpcProxy.map { grpcProxy =>
      grpcProxy.proxyUrl match {
        case Some(proxyUrl) => MnpClient.connectViaProxy(proxyUrl, address)
        case None           => MnpClient.connect(address)
      }
    }
  }

  override def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse] =
    logErrors(s"StartWorker ${startWorkerRequest.id}") {
      val converter = KubernetesConverter(config, kubernetesHost)
      val internalId = UUID.randomUUID().toString
      val converted = converter.convertStartWorkRequest(internalId, startWorkerRequest)

      val t0 = System.currentTimeMillis()
      namespaceFuture.flatMap { _ =>
        converted.create(Some(namespace), ops).flatMap { created =>
          val t1 = System.currentTimeMillis()
          val ingressUrl = created.ingressUrl(config, kubernetesHost)
          logger.info(
            s"Created Worker ${created.service.name} within ${t1 - t0}ms (ingress=${ingressUrl}, userId=${startWorkerRequest.id}, internalId=${internalId})"
          )
          waitWorkerAlive(internalId).map { _ =>
            StartWorkerResponse(
              nodeName = created.service.name,
              internalUrl = created.internalUrl,
              externalUrl = ingressUrl
            )
          }
        }
      }
    }

  private def waitWorkerAlive(internalId: String): Future[Unit] = {
    FutureHelper.tryMultipleTimes(
      config.kubernetes.defaultTimeout,
      config.kubernetes.defaultRetryInterval
    ) {
      listWorkersImpl(
        nameFilter = None,
        userIdFilter = None,
        internalIdFilter = Some(internalId)
      ).transform {
        case Failure(e)                        => Failure(e)
        case Success(values) if values.isEmpty => Failure(new Errors.InternalException(s"Workload is missing"))
        case Success(values) if values.exists(_.workerState.isFailure) =>
          val error = values.map(_.workerState).collect { case WorkerState.Failed(_, Some(error)) =>
            error
          }
          Failure(new Errors.CouldNotExecutePayload(s"Worker failed: ${error.mkString}"))
        case Success(values) if values.exists(_.workerState == WorkerState.Running) => Success(Some(()))
        case Success(values) if values.exists(_.workerState == WorkerState.Succeeded) =>
          Failure(new Errors.InternalException(s"Container directly succeeded?"))
        case Success(values) =>
          logger.info(s"Worker ${internalId} not yet ready: ${values.map(_.workerState)}")
          Success(None)
      }
    }
  }

  override def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse] =
    logErrors(s"ListWorkers") {
      listWorkersImpl(listWorkerRequest.nameFilter, listWorkerRequest.idFilter).map { workloads =>
        model.ListWorkerResponse(
          workloads.map { workload =>
            generateListWorkerResponseElement(workload)
          }
        )
      }
    }

  private def listWorkersImpl(
      nameFilter: Option[String],
      userIdFilter: Option[String],
      internalIdFilter: Option[String] = None
  ): Future[Vector[Workload]] = {
    val labels = Seq(
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue
    ) ++ userIdFilter.map { id =>
      LabelConstants.UserIdLabelName -> KubernetesNamer.encodeLabelValue(id)
    } ++ internalIdFilter.map { id =>
      LabelConstants.InternalIdLabelName -> id /* Internal ids are not encoded */
    }
    Workload.list(Some(namespace), nameFilter, labels, ops)
  }

  private def generateListWorkerResponseElement(workload: Workload): ListWorkerResponseElement = {
    val ingressUrl = workload.ingressUrl(config, kubernetesHost)
    model.ListWorkerResponseElement(
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
      `type` = workload.workerType,
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
        workload.stop(stopWorkerRequest.remove, ops)
      }

      Future.sequence(subFutures).map { _ =>
        model.StopWorkerResponse(
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

class KubernetesExecutorProvider @Inject() (metrics: WorkerMetrics, payloadProvider: PayloadProvider)(
    implicit akkaRuntime: AkkaRuntime
) extends Provider[Executor] {

  override def get(): Executor = {
    import ai.mantik.componently.AkkaHelper._
    val config = Config.fromTypesafeConfig(akkaRuntime.config)
    val kubernetesClient = skuber.k8sInit
    val k8sOperations = new K8sOperations(config, kubernetesClient)
    val backend = new KubernetesWorkerExecutorBackend(config, k8sOperations)
    new WorkerBasedExecutor(backend, metrics, payloadProvider)
  }
}

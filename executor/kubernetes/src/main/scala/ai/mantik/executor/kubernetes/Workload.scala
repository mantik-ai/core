package ai.mantik.executor.kubernetes

import java.time.Instant
import java.time.temporal.ChronoUnit

import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.model.WorkerState
import com.typesafe.scalalogging.Logger
import skuber.apps.v1.Deployment
import skuber.ext.Ingress
import skuber.ext.Ingress.Backend
import skuber.{ ObjectResource, Pod, ResourceDefinition, Service }
import skuber.json.batch.format._
import skuber.json.ext.format._
import skuber.json.format._
import skuber.json.apps.format.depFormat
import skuber.json.apps.format.deployListFormat
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A Workload as being used by the Executor
 * which can be sent to Kubernetes consisting
 * of different Kubernetes Items.
 *
 * Can also be retrieved by Kubernetes.
 *
 * @param loaded if true, the information is fetched from kubernetes
 */
case class Workload(
    internalId: String,
    pod: Option[Pod],
    deployment: Option[Deployment],
    service: Service,
    // note: the ingress may not be linked to the service yet
    ingress: Option[Ingress] = None,
    loaded: Boolean = false
) {

  /** Create items within kubernetes. */
  def create(namespace: Option[String], ops: K8sOperations)(implicit ec: ExecutionContext): Future[Workload] = {
    for {
      createdPod <- ops.maybeCreate(namespace, pod)
      createdDeployment <- ops.maybeCreate(namespace, deployment)
      createdService <- ops.create(namespace, service)
      updatedIngress = ingress.map(ingress => updateIngressMainService(ingress, createdService))
      createdIngress <- ops.maybeCreate(namespace, updatedIngress)
    } yield {
      Workload(
        internalId = internalId,
        pod = createdPod,
        deployment = createdDeployment,
        service = createdService,
        ingress = createdIngress,
        loaded = true
      )
    }
  }

  /** Stop a workload. */
  def stop(remove: Boolean, ops: K8sOperations, killReason: Option[String] = None)(implicit ec: ExecutionContext): Future[Unit] = {
    def ns[T <: ObjectResource](value: T): Option[String] = {
      Some(value.namespace).filter(_.nonEmpty)
    }
    def delete[T <: ObjectResource: ResourceDefinition](value: T): Future[Unit] = {
      ops.delete[T](ns(value), value.name)
    }
    def maybeDelete[T <: ObjectResource: ResourceDefinition](maybe: Option[T]): Future[Unit] = {
      maybe.map { value =>
        delete(value)
      }.getOrElse(Future.successful(()))
    }
    if (remove) {
      for {
        _ <- maybeDelete(pod)
        _ <- maybeDelete(deployment)
        _ <- delete(service)
        _ <- maybeDelete(ingress)
      } yield ()
    } else {

      // Always mark kill status, otherwise we may not know afterwards that we killed it.
      val reason = killReason.getOrElse("Stopped")
      ops.sendKillPatch[Service](service.name, ns(service), reason).flatMap { _ =>
        deployment match {
          case Some(definedDeployment) =>
            // Scale to 0 (also see workerState)
            ops.deploymentSetReplicas(ns(definedDeployment), definedDeployment.name, 0).map { _ => () }
          case None =>
            // Kubernetes has no way to stop pods. So we remove it
            maybeDelete(pod)
        }
      }
    }
  }

  def workerState: WorkerState = {
    val podState = for {
      status <- pod.flatMap(_.status)
      containerStatus <- status.containerStatuses.headOption
      state <- containerStatus.state
    } yield {
      val result = state match {
        case _: skuber.Container.Running => WorkerState.Running
        case t: skuber.Container.Terminated =>
          t.exitCode match {
            case 0     => WorkerState.Succeeded
            case other => WorkerState.Failed(other)
          }
        case t: skuber.Container.Waiting =>
          WorkerState.Pending
        case other =>
          WorkerState.Pending
      }
      result
    }

    // If deployment is scaled to 0 then it's like stopped
    val deploymentStopped = for {
      d <- deployment
      spec <- d.spec
      replicas <- spec.replicas
      if replicas == 0
    } yield WorkerState.Succeeded

    val killStatus = service.metadata.annotations.get(KubernetesConstants.KillAnnotationName).map { reason =>
      WorkerState.Failed(255, Some(reason))
    }

    killStatus.orElse(
      deploymentStopped
    ).orElse(
      podState
    ).getOrElse(
      WorkerState.Pending
    )
  }

  /** Check if this workload has a broken image. */
  def hasBrokenImage(config: Config, currentTime: Instant): Option[String] = {
    pod.flatMap { pod =>
      Workload.podImageError(config, currentTime, pod)
    }
  }

  private def updateIngressMainService(ingress: Ingress, service: Service): Ingress = {
    val port = service.spec.flatMap(_.ports.headOption).map(_.port).getOrElse(80)
    def updateBackend(backend: Backend): Backend = {
      backend.copy(
        serviceName = service.name,
        servicePort = port
      )
    }
    ingress.copy(
      spec = ingress.spec.map { spec =>
        spec.copy(
          rules = spec.rules.map { rule =>
            rule.copy(
              http = rule.http.copy(
                paths = rule.http.paths.map { path =>
                  path.copy(
                    backend = updateBackend(path.backend)
                  )
                }
              )
            )
          },
          backend = spec.backend.map(updateBackend)
        )
      }
    )
  }

  /** Returns the URL of the ingress. */
  def ingressUrl(config: Config, kubernetesHost: String): Option[String] = {
    ingress.map { ingress =>
      IngressConverter(config, kubernetesHost, ingress.name).ingressUrl
    }
  }

}

object Workload {
  val logger = Logger(getClass)

  /** List Workloads */
  def list(namespace: Option[String], serviceNameFilter: Option[String], labelFilters: Seq[(String, String)], ops: K8sOperations)(implicit ec: ExecutionContext): Future[Vector[Workload]] = {
    serviceNameFilter match {
      case Some(serviceName) =>
        ops.byName[Service](namespace, serviceName).flatMap { maybeService =>
          val validService = maybeService.filter { service =>
            labelFilters.forall {
              case (key, value) =>
                service.metadata.labels.get(key).contains(value)
            }
          }
          expand(namespace, validService.toVector, ops, labelFilters)
        }
      case None =>
        ops.listSelected[Service](namespace, labelFilters).flatMap { services =>
          expand(namespace, services.toVector, ops, labelFilters)
        }
    }
  }

  def byInternalId(namespace: String, internalId: String, ops: K8sOperations)(implicit ec: ExecutionContext): Future[Option[Workload]] = {
    val labelFilter = Seq(
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
      LabelConstants.InternalIdLabelName -> internalId
    )
    val podFuture = ops.listSelected[Pod](Some(namespace), labelFilter)
    val serviceFuture = ops.listSelected[Service](Some(namespace), labelFilter)
    val deploymentFuture = ops.listSelected[Deployment](Some(namespace), labelFilter)
    val ingressFuture = ops.listSelected[Ingress](Some(namespace), labelFilter)
    for {
      pods <- podFuture
      services <- serviceFuture
      deployments <- deploymentFuture
      ingresses <- ingressFuture
    } yield {
      correlate(services.toVector, pods.toVector, deployments.toVector, ingresses.toVector).headOption
    }
  }

  /** List Workloads which are pending in all namespaces */
  def listPending(ops: K8sOperations)(implicit ec: ExecutionContext): Future[Vector[Workload]] = {
    ops.getAllManagedPendingPods().flatMap { namespacePods =>
      val namespaceInternalIds = namespacePods.toVector.flatMap {
        case (namespace, podList) =>
          podList.flatMap(_.metadata.labels.get(LabelConstants.InternalIdLabelName)).map { internalId =>
            namespace -> internalId
          }
      }
      val futures = namespaceInternalIds.map {
        case (namespace, internalId) =>
          byInternalId(namespace, internalId, ops)
      }
      Future.sequence(futures).map { results =>
        results.flatten
      }
    }
  }

  /** Figure out other related items by finding sibling of the services. */
  def expand(namespace: Option[String], services: Vector[Service], ops: K8sOperations, labelFilters: Seq[(String, String)])(
    implicit
    ec: ExecutionContext
  ): Future[Vector[Workload]] = {
    if (services.isEmpty) {
      return Future.successful(Vector.empty)
    }

    val internalIds = services.flatMap { service =>
      service.metadata.labels.get(LabelConstants.InternalIdLabelName)
    }

    // Optimization: if it's only one element, we can use it's internalId to load it directly
    val labelsToUse = internalIds match {
      case Vector(internalId) =>
        labelFilters :+ (LabelConstants.InternalIdLabelName -> internalId)
      case _ =>
        labelFilters
    }

    val podsFuture = ops.listSelected[Pod](namespace, labelsToUse)
    val deploymentsFuture = ops.listSelected[Deployment](namespace, labelsToUse)
    val ingressFuture = ops.listSelected[Ingress](namespace, labelsToUse)

    for {
      pods <- podsFuture
      deployments <- deploymentsFuture
      ingresses <- ingressFuture
    } yield {
      correlate(services, pods.toVector, deployments.toVector, ingresses.toVector)
    }
  }

  private def correlate(services: Vector[Service], pods: Vector[Pod], deployments: Vector[Deployment], ingresses: Vector[Ingress]): Vector[Workload] = {
    def makeLookup[T <: ObjectResource](in: Vector[T]): Map[String, T] = {
      in.flatMap { x =>
        x.metadata.labels.get(LabelConstants.InternalIdLabelName).map { internalId =>
          internalId -> x
        }
      }.toMap
    }

    val podMap = makeLookup(pods)
    val deploymentMap = makeLookup(deployments)
    val ingressMap = makeLookup(ingresses)
    for {
      service <- services
      internalId <- service.metadata.labels.get(LabelConstants.InternalIdLabelName)
      pod = podMap.get(internalId)
      deployment = deploymentMap.get(internalId)
      ingress = ingressMap.get(internalId)
    } yield {
      Workload(
        internalId,
        pod,
        deployment,
        service,
        ingress,
        loaded = true
      )
    }
  }

  /**
   * Check if a pod has an image error, so that we can kill it.
   * @return the first image error found.
   */
  def podImageError(config: Config, currentTime: Instant, pod: Pod): Option[String] = {
    // See: https://github.com/kubernetes/kubernetes/blob/d24fe8a801748953a5c34fd34faa8005c6ad1770/pkg/kubelet/images/types.go

    // Errors which will lead to termination immediately
    val ImmediatelyFailErros = Seq(
      "ErrImageNeverPull",
      "InvalidImageName",
      "ImageInspectError"
    )

    // Errors which will lead to termination if the timeout is reached
    val Errors = Set(
      "ImagePullBackOff",
      "ErrImagePull",
      "RegistryUnavailable"
    )

    /** Look if the container image has a status which should be terminated. */
    def imageError(startTime: skuber.Timestamp, container: skuber.Container.Status): Option[String] = {
      container.state match {
        case Some(w: skuber.Container.Waiting) =>
          w.reason match {
            case Some(reason) if ImmediatelyFailErros.contains(reason) =>
              Some(reason)
            case Some(reason) if Errors.contains(reason) && reachedTimeout(startTime) =>
              Some(reason)
            case Some(reason) =>
              logger.trace(s"Pod ${pod.name} reached a status ${reason} which is not yet worth to terminate")
              None
            case _ => None
          }
        case _ => None
      }
    }

    /** Check if startTime reached the timeout. */
    def reachedTimeout(startTime: skuber.Timestamp): Boolean = {
      startTime.toInstant.plus(config.kubernetes.podPullImageTimeout.toMillis, ChronoUnit.MILLIS).isBefore(currentTime)
    }

    val imageErrors = for {
      status <- pod.status.toList
      startTime <- status.startTime.toList
      if status.phase.contains(Pod.Phase.Pending)
      containerStatus <- status.containerStatuses
      if !containerStatus.ready
      imageError <- imageError(startTime, containerStatus)
    } yield imageError

    imageErrors.headOption
  }
}

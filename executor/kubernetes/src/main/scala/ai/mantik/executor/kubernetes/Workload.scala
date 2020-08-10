package ai.mantik.executor.kubernetes

import ai.mantik.executor.model.WorkerState
import skuber.apps.Deployment
import skuber.ext.Ingress
import skuber.ext.Ingress.Backend
import skuber.{ ObjectResource, Pod, ResourceDefinition, Service }
import skuber.json.batch.format._
import skuber.json.ext.format._
import skuber.json.format._
import skuber.json.apps.format.depFormat
import skuber.json.apps.format.deployListFormat

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
  def stop(remove: Boolean, ops: K8sOperations)(implicit ec: ExecutionContext): Future[Unit] = {
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

  def workerState: WorkerState = {
    val podState = for {
      status <- pod.flatMap(_.status)
      containerStatus <- status.containerStatuses.headOption
      state <- containerStatus.state
    } yield {
      state match {
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
    }

    // If deployment is scaled to 0 then it's like stopped
    val deploymentStopped = for {
      d <- deployment
      spec <- d.spec
      replicas <- spec.replicas
      if replicas == 0
    } yield WorkerState.Succeeded

    podState.orElse {
      deploymentStopped
    }.getOrElse(WorkerState.Pending)
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

  /** Figure out other related items by finding sibling of the services. */
  def expand(namespace: Option[String], services: Vector[Service], ops: K8sOperations, labelFilters: Seq[(String, String)])(
    implicit
    ec: ExecutionContext
  ): Future[Vector[Workload]] = {
    if (services.isEmpty) {
      return Future.successful(Vector.empty)
    }

    val internalIds = services.flatMap { service =>
      service.metadata.labels.get(KubernetesConstants.InternalId)
    }

    // Optimization: if it's only one element, we can use it's internalId to load it directly
    val labelsToUse = internalIds match {
      case Vector(internalId) =>
        labelFilters :+ (KubernetesConstants.InternalId -> internalId)
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
        x.metadata.labels.get(KubernetesConstants.InternalId).map { internalId =>
          internalId -> x
        }
      }.toMap
    }

    val podMap = makeLookup(pods)
    val deploymentMap = makeLookup(deployments)
    val ingressMap = makeLookup(ingresses)
    for {
      service <- services
      internalId <- service.metadata.labels.get(KubernetesConstants.InternalId)
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
}

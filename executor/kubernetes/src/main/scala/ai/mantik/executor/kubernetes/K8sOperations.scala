package ai.mantik.executor.kubernetes

import java.time.Clock
import java.time.temporal.ChronoUnit

import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.executor.Errors.InternalException
import ai.mantik.executor.common.LabelConstants
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

  /**
   * Figures out the name/ip of Kubernetes we are talking to.
   * (For ingress interpolation)
   */
  def clusterServer: Uri = {
    Uri(rootClient.clusterServer)
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
            LabelSelector.IsEqualRequirement(LabelConstants.ManagedByLabelName, LabelConstants.ManagedByLabelValue),
            LabelSelector.IsEqualRequirement(LabelConstants.RoleLabelName, LabelConstants.role.worker)
          )),
          fieldSelector = Some("status.phase==Pending")
        )
      ).map(_.items)
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

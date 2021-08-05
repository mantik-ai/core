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

import java.time.Clock
import java.time.temporal.ChronoUnit

import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.Errors.InternalException
import ai.mantik.executor.common.LabelConstants
import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import play.api.libs.json.{Format, JsObject, JsValue, Json, Writes}
import skuber.Container.Running
import skuber.api.client.{KubernetesClient, Status, WatchEvent}
import skuber.api.patch.{JsonMergePatch, MetadataPatch}
import skuber.apps.v1.Deployment
import skuber.apps.v1.ReplicaSet
import skuber.batch.Job
import skuber.ext.Ingress
import skuber.json.batch.format._
import skuber.json.ext.format._
import skuber.json.format._
import skuber.{
  Container,
  K8SException,
  KListItem,
  LabelSelector,
  ListResource,
  Namespace,
  ObjectResource,
  Pod,
  ResourceDefinition,
  Secret,
  Service
}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.{Failure, Success}

/** Wraps Kubernetes Operations */
class K8sOperations(config: Config, rootClient: KubernetesClient)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase {

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
  def create[O <: ObjectResource](
      namespace: Option[String],
      obj: O
  )(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
    errorHandling(s"Create ${rd.spec.defaultVersion}/${rd.spec.names.singular}") {
      namespacedClient(namespace).create(obj)
    }
  }

  /** Like create, but ignores argument if it's None. */
  def maybeCreate[O <: ObjectResource](
      namespace: Option[String],
      maybeObj: Option[O]
  )(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Option[O]] = {
    maybeObj
      .map { obj =>
        create(namespace, obj).map {
          Some(_)
        }
      }
      .getOrElse(
        Future.successful(None)
      )
  }

  /** Creates or replaces */
  def createOrReplace[O <: ObjectResource](
      namespace: Option[String],
      obj: O
  )(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
    val client = namespacedClient(namespace)
    val description = s"${obj.apiVersion}/${obj.kind}"
    errorHandling(s"Create or replace ${description}", silent = true)(client.create(obj)).recoverWith {
      case e: K8SException if e.status.code.contains(409) =>
        // We could also try to use the Patch API, but this is very tricky to do it in a generic way
        logger.info(s"${obj.kind} ${obj.name} already exists, deleting...")
        for {
          _ <- errorHandling("Deleting")(
            client.delete[O](obj.name, gracePeriodSeconds = config.kubernetes.deletionGracePeriod.toSeconds.toInt)
          )
          _ = logger.debug(s"Waiting for deletion of $description")
          _ <- waitUntilDeleted[O](namespace, obj.name)
          _ = logger.info(s"Recreating ${obj.kind} ${obj.name}")
          r <- errorHandling(s"Creating ${description}")(client.create(obj))
        } yield r
    }
  }

  private def waitUntilDeleted[O <: ObjectResource](
      namespace: Option[String],
      name: String
  )(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Unit] = {
    val client = namespacedClient(namespace)
    def tryFunction(): Future[Option[Boolean]] = {
      errorHandling(s"Get ${rd.spec.defaultVersion}/${rd.spec.names.singular}") {
        client.getOption[O](name).map {
          case None    => Some(true)
          case Some(_) => None
        }
      }
    }
    FutureHelper
      .tryMultipleTimes(config.kubernetes.defaultTimeout, config.kubernetes.defaultRetryInterval)(tryFunction())
      .map { _ =>
        ()
      }
  }

  def deploymentSetReplicas(namespace: Option[String], deploymentName: String, replicas: Int): Future[Deployment] = {
    val jsonRequest = Json.obj(
      "replicas" -> replicas
    )
    jsonPatch[Deployment](namespace, deploymentName, jsonRequest)
  }

  def jsonPatch[O <: ObjectResource](namespace: Option[String], name: String, jsonRequest: JsValue)(
      implicit fmt: Format[O],
      rd: ResourceDefinition[O]
  ): Future[O] = {
    case object data extends JsonMergePatch
    implicit val writes: Writes[data.type] = Writes { _ =>
      Json.obj("spec" -> jsonRequest)
    }
    val client = namespacedClient(namespace)
    errorHandling(s"jsonPatch ${rd.spec.defaultVersion}/${rd.spec.names.singular}") {
      client.patch[data.type, O](name, data)
    }
  }

  /** Delete an object in Kubernetes. */
  def delete[O <: ObjectResource](
      namespace: Option[String],
      name: String,
      gracePeriodSecondsOverride: Option[Int] = None
  )(
      implicit rd: ResourceDefinition[O]
  ): Future[Unit] = {
    val gracePeriodSeconds = gracePeriodSecondsOverride.getOrElse(config.kubernetes.deletionGracePeriod.toSeconds.toInt)
    logger.debug(s"Deleting ${rd.spec.names.singular} of name ${name}, gracePeriod=${gracePeriodSeconds}")
    errorHandling(s"Delete ${rd.spec.defaultVersion}/${rd.spec.names.singular}") {
      namespacedClient(namespace).delete(name, gracePeriodSeconds)
    }
  }

  /** Ensure the existence of a namespace, and returns a kubernetes client for this namespace. */
  def ensureNamespace(namespace: String): Future[Namespace] = {
    errorHandling("Get namespace", true)(rootClient.get[Namespace](namespace))
      .map { ns =>
        logger.trace(s"Using existing namespace ${namespace}, no creation necessary")
        ns
      }
      .recoverWith {
        case e: K8SException if e.status.code.contains(404) =>
          logger.info(s"Namespace ${namespace} doesn't exist, trying to create")
          errorHandling("Create Namespace")(rootClient.create(Namespace(namespace))).andThen {
            case Success(_)     => logger.info(s"Namespace ${namespace} on the fly created")
            case Failure(error) => logger.error(s"Creating namespace ${namespace} failed", error)
          }
      }
  }

  /** Returns all Pods which are managed by this executor and which are in pending state. */
  def getAllManagedPendingPods(): Future[Map[String, List[Pod]]] = {
    errorHandling("Get Managed Namespaces")(getManagedNamespaces()).flatMap { namespaces =>
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

  /** Returns pending pods managed by this executor. . */
  private def getPendingPods(namespace: Option[String] = None): Future[List[Pod]] = {
    errorHandling("getPendingPods") {
      namespacedClient(namespace)
        .listWithOptions[ListResource[skuber.Pod]](
          skuber.ListOptions(
            labelSelector = Some(
              LabelSelector(
                LabelSelector.IsEqualRequirement(LabelConstants.ManagedByLabelName, LabelConstants.ManagedByLabelValue),
                LabelSelector.IsEqualRequirement(LabelConstants.RoleLabelName, LabelConstants.role.worker)
              )
            ),
            fieldSelector = Some("status.phase==Pending")
          )
        )
        .map(_.items)
    }
  }

  def listSelected[T <: KListItem](namespace: Option[String], labelFilter: Seq[(String, String)])(
      implicit format: Format[ListResource[T]],
      rd: ResourceDefinition[ListResource[T]]
  ): Future[ListResource[T]] = {
    val labelSelector = LabelSelector(
      (labelFilter.map { case (k, v) =>
        LabelSelector.IsEqualRequirement(k, v)
      }.toVector): _*
    )
    errorHandling(s"ListSelected ${rd.spec.defaultVersion}/${rd.spec.names.singular}") {
      namespacedClient(namespace).listSelected[ListResource[T]](
        labelSelector
      )
    }
  }

  def byName[T <: ObjectResource](namespace: Option[String], name: String)(
      implicit format: Format[T],
      resourceDefinition: ResourceDefinition[T]
  ): Future[Option[T]] = {
    errorHandling(
      s"byName ${resourceDefinition.spec.defaultVersion}/${resourceDefinition.spec.names.singular} ${namespace}/${name}"
    ) {
      namespacedClient(namespace).getOption[T](name)
    }
  }

  /** Add Kill-Annotations to some items. */
  def sendKillPatch[T <: ObjectResource](name: String, namespace: Option[String], reason: String)(
      implicit fmt: Format[T],
      rd: ResourceDefinition[T]
  ): Future[Unit] = {
    val patch = MetadataPatch(annotations =
      Some(
        Map(
          KubernetesConstants.KillAnnotationName -> reason
        )
      )
    )
    errorHandling("sendKillPatch") {
      rootClient.patch[MetadataPatch, T](name, patch, namespace)
    }.map { _ => () }
  }

  /**
    * Retries an operation on Error 500 Errors.
    * @param what name for the operation executed
    * @param silent if true, when do not log on regular errors
    * @param f the operation to run
    */
  def errorHandling[T](what: => String, silent: Boolean = false)(f: => Future[T]): Future[T] = {
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
          if (!silent) {
            logger.warn(s"K8S ${what} Operation failed, tried ${config.kubernetes.retryTimes}")
          }
          response.tryFailure(e)
        case Failure(s: K8SException) if s.status.code.contains(500) && retryAfterSeconds(s.status).nonEmpty =>
          val retryValue = retryAfterSeconds(s.status).get
          val seconds = Math.min(Math.max(1, retryValue), config.kubernetes.defaultTimeout.toSeconds)
          if (!silent) {
            logger.warn(
              s"K8S ${what} Operation failed with 500+retry ${retryValue}, will try again in ${seconds} seconds."
            )
          }
          actorSystem.scheduler.scheduleOnce(seconds.seconds) {
            tryAgain(remaining - 1)
          }
        case Failure(e) =>
          if (!silent) {
            logger.debug(s"K8S ${what} operation failed", e)
          }
          response.tryFailure(e)
      }
    }

    tryAgain(config.kubernetes.retryTimes)
    response.future
  }
}

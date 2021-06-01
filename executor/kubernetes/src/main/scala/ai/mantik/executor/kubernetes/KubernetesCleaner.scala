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

import org.slf4j.LoggerFactory
import play.api.libs.json.Format
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import skuber.batch.Job
import skuber.ext.Ingress
import skuber.json.batch.format._
import skuber.json.ext.format._
import skuber.json.format._
import skuber.json.apps.format.deployListFormat
import skuber.{ConfigMap, ListResource, Namespace, ObjectResource, Pod, ResourceDefinition, Secret, Service}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

/** Helper for deleting Kubernetes content. Used for Integration Tests. */
private[mantik] class KubernetesCleaner(rootClient: KubernetesClient, config: Config) {

  val logger = LoggerFactory.getLogger(getClass)

  import ExecutionContext.Implicits.global

  /** Delete all managed Kubernetes content. For Integration Tests. */
  def deleteKubernetesContent(): Unit = {
    logger.info("Deleting Kubernetes Content...")
    val pendingNamespaces =
      await("Get Namespaces", rootClient.getNamespaceNames).filter(_.startsWith(config.kubernetes.namespacePrefix))
    pendingNamespaces.foreach { namespace =>
      logger.info(s"Deleting namespace ${namespace}")
      val namespaced = rootClient.usingNamespace(namespace)
      // Starting them all at the same time
      val deploymentFuture = fastDeleteAll[Deployment](namespaced)
      val jobFuture = fastDeleteAll[Job](namespaced)
      val ingressFuture = fastDeleteAll[Ingress](namespaced)

      val mainFuture = for {
        _ <- deploymentFuture
        _ <- jobFuture
        _ <- ingressFuture
        _ <- fastDeleteAll[Service](namespaced)
        _ <- fastDeleteAll[Pod](namespaced) // pods after the others
        _ <- fastDeleteAll[ConfigMap](namespaced)
        _ <- fastDeleteAll[Secret](namespaced)
        _ <- rootClient.delete[Namespace](namespace, gracePeriodSeconds = 0)
      } yield {
        ()
      }

      await("Deletion", mainFuture)
    }
    pendingNamespaces.foreach { namespace =>
      waitForNamespaceDeletion(namespace)
    }
    logger.info("Deleting Kubernetes Content finished")
  }

  private def fastDeleteAll[O <: ObjectResource](namespaced: KubernetesClient)(
      implicit lrd: ResourceDefinition[ListResource[O]],
      ord: ResourceDefinition[O],
      f: Format[ListResource[O]]
  ): Future[Unit] = {
    namespaced.list[ListResource[O]]().flatMap { elements =>
      val deletionRequests = elements.items.map { element =>
        val subResult = namespaced.delete[O](element.name, gracePeriodSeconds = 0)
        subResult.failed.foreach { error =>
          logger.error(s"Error deleting ${element.name}", error)
        }
        subResult
      }
      Future.sequence(deletionRequests).map { _ => () }
    }
  }

  @tailrec
  private def waitForNamespaceDeletion(namespace: String): Unit = {
    val namespaces = await("Get NamespaceNames", rootClient.getNamespaceNames)
    if (namespaces.contains(namespace)) {
      logger.info(s"Namespace ${namespace} still exists, waiting")
      Try {
        val namespaceContent = rootClient.usingNamespace(namespace)
        listPresent[Service](namespaceContent)
        listPresent[Pod](namespaceContent)
        listPresent[ConfigMap](namespaceContent)
        listPresent[Ingress](namespaceContent)
      }
      Thread.sleep(1000)
      waitForNamespaceDeletion(namespace)
    }
  }

  /** List present k8s resources of a type inside a Namespace (for debugging purposes) */
  private def listPresent[O <: ObjectResource](
      namespacedClient: KubernetesClient
  )(implicit lrd: ResourceDefinition[ListResource[O]], ord: ResourceDefinition[O], f: Format[ListResource[O]]): Unit = {
    val name = lrd.spec.names.singular
    val elements = await(s"get${name}", namespacedClient.list[ListResource[O]]())
    logger.debug(s"${name} count: ${elements.size}")
    elements.foreach { element =>
      logger.debug(s"  - Item ${element.name}")
    }
  }

  private def await[T](what: String, f: Future[T]): T = {
    try {
      Await.result(f, 60.seconds)
    } catch {
      case e: Exception =>
        logger.error(s"${what} failed", e)
        throw e
    }
  }
}

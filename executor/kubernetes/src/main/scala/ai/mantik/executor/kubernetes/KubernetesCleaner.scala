package ai.mantik.executor.kubernetes

import org.slf4j.LoggerFactory
import skuber.api.client.KubernetesClient
import skuber.batch.Job
import skuber.ext.Ingress
import skuber.json.batch.format._
import skuber.json.ext.format._
import skuber.json.format._
import skuber.{ ConfigMap, ListResource, Namespace, Pod }

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

/** Helper for deleting Kubernetes content. Used for Integration Tests. */
private[mantik] class KubernetesCleaner(rootClient: KubernetesClient, config: Config) {

  val logger = LoggerFactory.getLogger(getClass)

  /** Delete all managed Kubernetes content. For Integration Tests. */
  def deleteKubernetesContent(): Unit = {
    logger.info("Deleting Kubernetes Content...")
    val pendingNamespaces = await(rootClient.getNamespaceNames).filter(_.startsWith(config.kubernetes.namespacePrefix))

    pendingNamespaces.foreach { namespace =>
      logger.info(s"Deleting namespace ${namespace}")
      val namespaced = rootClient.usingNamespace(namespace)
      await(namespaced.deleteAll[ListResource[ConfigMap]]())
      await(namespaced.deleteAll[ListResource[Pod]]())
      await(namespaced.deleteAll[ListResource[Job]]())
      await(namespaced.deleteAll[ListResource[Ingress]]())
      // await(namespaced.deleteAll[ListResource[Service]]()) // error 405 ?!
      await(rootClient.delete[Namespace](namespace, gracePeriodSeconds = 0))
    }
    pendingNamespaces.foreach { namespace =>
      waitForNamespaceDeletion(namespace)
    }
    logger.info("Deleting Kubernetes Content finished")
  }

  @tailrec
  private def waitForNamespaceDeletion(namespace: String): Unit = {
    val namespaces = await(rootClient.getNamespaceNames)
    if (namespaces.contains(namespace)) {
      logger.info(s"Namespace ${namespace} still exists, waiting")
      Thread.sleep(1000)
      waitForNamespaceDeletion(namespace)
    }
  }

  private def await[T](f: Future[T]): T = Await.result(f, 60.seconds)

}

package io.mantik.executor.integration

import java.time.Clock

import io.mantik.executor.{Config, Executor}
import io.mantik.executor.testutils.{AkkaSupport, TestBase}
import org.scalatest.time.{Millis, Span}
import skuber.{ConfigMap, ListResource, Namespace, Pod}
import skuber.api.client.KubernetesClient
import skuber.batch.Job
import skuber.json.format._
import skuber.json.batch.format._

import scala.annotation.tailrec
import scala.concurrent.duration._

abstract class KubernetesTestBase extends TestBase with AkkaSupport {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  val config = Config().copy(
    namespacePrefix = "systemtest-",
    podTrackerId = "mantik-executor",
    podPullImageTimeout = 3.seconds,
    checkPodInterval = 1.second,
    defaultTimeout = 10.seconds,
    defaultRetryInterval = 1.second,
    interface = "localhost",
    port = 15001,
  )

  protected var _kubernetesClient: KubernetesClient = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    _kubernetesClient = skuber.k8sInit(actorSystem, materializer)
    implicit val clock = Clock.systemUTC()
    deleteKubernetesContent()
  }

  override protected def afterAll(): Unit = {
    _kubernetesClient.close
    super.afterAll()
  }

  private def deleteKubernetesContent(): Unit = {
    val pendingNamespaces = await(_kubernetesClient.getNamespaceNames).filter(_.startsWith(config.namespacePrefix))
    pendingNamespaces.foreach { namespace =>
      logger.info(s"Deleting namespace ${namespace}")
      val namespaced = _kubernetesClient.usingNamespace(namespace)
      await(namespaced.deleteAll[ListResource[ConfigMap]]())
      await(namespaced.deleteAll[ListResource[Pod]]())
      await(namespaced.deleteAll[ListResource[Job]]())
      await(_kubernetesClient.delete[Namespace](namespace, gracePeriodSeconds = 0))
      logger.info(s"Waiting for final deletion...")
      waitForNamespaceDeletion(namespace)
    }
  }

  @tailrec
  private def waitForNamespaceDeletion(namespace: String): Unit = {
    val namespaces = await(_kubernetesClient.getNamespaceNames)
    if (namespaces.contains(namespace)) {
      logger.info(s"Namespace ${namespace} still exists, waiting")
      Thread.sleep(1000)
      waitForNamespaceDeletion(namespace)
    }
  }

  protected trait Env {
    val kubernetesClient = _kubernetesClient
  }
}

package ai.mantik.executor.kubernetes

import com.typesafe.config.{Config => TypesafeConfig}

import scala.concurrent.duration.{Duration, FiniteDuration}
import ai.mantik.componently.utils.ConfigExtensions._

/**
  * Kubernetes part of [[Config]].
  *
  * @param namespacePrefix prefix to use for namespace creation
  * @param retryTimes how often something is retried if kubernetes asks for.
  * @param podPullImageTimeout timeout, after which pods are killed when they can't find their image.
  * @param checkPodInterval how often the pods are checked.
  * @param defaultTimeout when a regular operation times out
  * @param defaultRetryInterval after which period something is retried again.
  */
case class KubernetesConfig(
    namespacePrefix: String,
    retryTimes: Int,
    podPullImageTimeout: Duration,
    checkPodInterval: Duration,
    defaultTimeout: FiniteDuration,
    defaultRetryInterval: FiniteDuration,
    ingressSubPath: Option[String],
    ingressRemoteUrl: String,
    ingressAnnotations: Map[String, String],
    nodeAddress: Option[String]
)

object KubernetesConfig {
  def fromTypesafe(c: TypesafeConfig): KubernetesConfig = {
    KubernetesConfig(
      namespacePrefix = c.getString("behavior.namespacePrefix"),
      retryTimes = c.getInt("behavior.retryTimes"),
      podPullImageTimeout = c.getFiniteDuration("behavior.podPullImageTimeout"),
      checkPodInterval = c.getFiniteDuration("behavior.checkPodInterval"),
      defaultTimeout = c.getFiniteDuration("behavior.defaultTimeout"),
      defaultRetryInterval = c.getFiniteDuration("behavior.retryInterval"),
      ingressSubPath = c.getOptionalString("ingress.subPath"),
      ingressRemoteUrl = c.getString("ingress.remoteUrl"),
      ingressAnnotations = c.getKeyValueMap("ingress.annotations"),
      nodeAddress = c.getOptionalString("nodeAddress")
    )
  }
}

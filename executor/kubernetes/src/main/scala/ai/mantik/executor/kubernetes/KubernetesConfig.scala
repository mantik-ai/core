package ai.mantik.executor.kubernetes

import com.typesafe.config.{ Config => TypesafeConfig }

import scala.collection.JavaConverters._
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * Kubernetes part of [[Config]].
 *
 * @param namespacePrefix prefix to use for namespace creation
 * @param retryTimes how often something is retried if kubernetes asks for.
 * @param disablePull if true, pulling of images will be disabled (good for integration tests)
 * @param podPullImageTimeout timeout, after which pods are killed when they can't find their image.
 * @param checkPodInterval how often the pods are checked.
 * @param defaultTimeout when a regular operation times out
 * @param defaultRetryInterval after which period something is retried again.
 */
case class KubernetesConfig(
    namespacePrefix: String,
    retryTimes: Int,
    disablePull: Boolean,
    podPullImageTimeout: Duration,
    checkPodInterval: Duration,
    defaultTimeout: FiniteDuration,
    defaultRetryInterval: FiniteDuration,
    ingressSubPath: Option[String],
    ingressRemoteUrl: String,
    ingressAnnotations: Map[String, String]
)

object KubernetesConfig {
  def fromTypesafe(c: TypesafeConfig): KubernetesConfig = {
    KubernetesConfig(
      namespacePrefix = c.getString("behavior.namespacePrefix"),
      retryTimes = c.getInt("behavior.retryTimes"),
      disablePull = c.getBoolean("disablePull"),
      podPullImageTimeout = c.getDuration("behavior.podPullImageTimeout"),
      checkPodInterval = c.getDuration("behavior.checkPodInterval"),
      defaultTimeout = c.getDuration("behavior.defaultTimeout"),
      defaultRetryInterval = c.getDuration("behavior.retryInterval"),
      ingressSubPath = getOptionalString(c, "ingress.subPath"),
      ingressRemoteUrl = c.getString("ingress.remoteUrl"),
      ingressAnnotations = getMap(c, "ingress.annotations")
    )
  }

  private def getOptionalString(c: TypesafeConfig, name: String): Option[String] = {
    if (c.getIsNull(name)) {
      None
    } else {
      Some(c.getString(name))
    }
  }

  private def getMap(c: TypesafeConfig, key: String): Map[String, String] = {
    val array = c.getObjectList(key).asScala
    array.map { sub =>
      val asConfig = sub.toConfig
      asConfig.getString("key") -> asConfig.getString("value")
    }.toMap
  }

  import scala.language.implicitConversions
  private implicit def convertDuration(d: java.time.Duration): scala.concurrent.duration.FiniteDuration = {
    Duration.fromNanos(d.toNanos)
  }
}

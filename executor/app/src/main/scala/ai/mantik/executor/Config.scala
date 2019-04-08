package ai.mantik.executor

import com.typesafe.config.{ ConfigFactory, Config => TypesafeConfig }
import ai.mantik.executor.model.Container

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.collection.JavaConverters._

/**
 * Configuration for the execution.
 *
 * @param sideCar defines the way the side car is started
 * @param coordinator defines the way the coordinator is started
 * @param payloadPreparer defines the way the payload-Preparer is started
 * @param namespacePrefix prefix to use for namespace creation
 * @param podTrackerId special id, so that the executor knows which pods to track state.
 * @param podPullImageTimeout timeout, after which pods are killed when they can't find their image.
 * @param checkPodInterval how often the pods are checked.
 * @param defaultTimeout when a regular operation times out
 * @param defaultRetryInterval after which period something is retried again.
 * @param interface interface to listen on
 * @param port port to listen on
 */
case class Config(
    sideCar: Container,
    coordinator: Container,
    payloadPreparer: Container,
    namespacePrefix: String,
    podTrackerId: String,
    podPullImageTimeout: Duration,
    checkPodInterval: Duration,
    defaultTimeout: FiniteDuration,
    defaultRetryInterval: FiniteDuration,
    interface: String,
    port: Int
)

object Config {

  def apply(): Config = fromTypesafeConfig(ConfigFactory.defaultApplication())

  /** Load settings from Config. */
  def fromTypesafeConfig(c: TypesafeConfig): Config = {
    Config(
      sideCar = parseContainer(c.getConfig("containers.sideCar")),
      coordinator = parseContainer(c.getConfig("containers.coordinator")),
      payloadPreparer = parseContainer(c.getConfig("containers.payloadPreparer")),
      namespacePrefix = c.getString("kubernetes.behavior.namespacePrefix"),
      podTrackerId = c.getString("app.podTrackerId"),
      podPullImageTimeout = c.getDuration("kubernetes.behavior.podPullImageTimeout"),
      checkPodInterval = c.getDuration("kubernetes.behavior.checkPodInterval"),
      defaultTimeout = c.getDuration("kubernetes.behavior.defaultTimeout"),
      defaultRetryInterval = c.getDuration("kubernetes.behavior.retryInterval"),
      interface = c.getString("app.server.interface"),
      port = c.getInt("app.server.port")
    )
  }

  private def parseContainer(c: TypesafeConfig): Container = {
    Container(
      image = c.getString("image"),
      parameters = c.getStringList("parameters").asScala
    )
  }

  private implicit def convertDuration(d: java.time.Duration): scala.concurrent.duration.FiniteDuration = {
    Duration.fromNanos(d.toNanos)
  }
}
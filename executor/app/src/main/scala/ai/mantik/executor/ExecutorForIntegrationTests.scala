package ai.mantik.executor

import java.time.Clock

import ai.mantik.executor.impl.{ ExecutorImpl, K8sOperations }
import ai.mantik.executor.server.ExecutorServer
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }

import scala.concurrent.ExecutionContext

/** An embedded executor for integration tests. */
class ExecutorForIntegrationTests(implicit actorSystem: ActorSystem, ec: ExecutionContext, materializer: Materializer) {

  // Override settings, so that images are directly pulled from minikube
  val overrideConfig = ConfigFactory.load()
    .withoutPath("docker.defaultImageTag")
    .withoutPath("docker.defaultImageRepository")
    .withValue("kubernetes.disablePull", ConfigValueFactory.fromAnyRef(true))

  val executorConfig = Config.fromTypesafeConfig(overrideConfig)
  implicit val clock = Clock.systemUTC()
  val kubernetesClient = skuber.k8sInit
  val k8sOperations = new K8sOperations(executorConfig, kubernetesClient)
  val executor = new ExecutorImpl(executorConfig, k8sOperations)
  val server = new ExecutorServer(executorConfig, executor)

  server.start()

  def shutdown(): Unit = {
    server.stop()
    executor.shutdown()
    kubernetesClient.close
  }
}

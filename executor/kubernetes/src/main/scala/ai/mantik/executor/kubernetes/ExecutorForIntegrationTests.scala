package ai.mantik.executor.kubernetes

import java.time.Clock

import ai.mantik.executor.server.{ ExecutorServer, ServerConfig }
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{ Config => TypesafeConfig }
import scala.concurrent.ExecutionContext

/** An embedded executor for integration tests. */
class ExecutorForIntegrationTests(config: TypesafeConfig)(implicit actorSystem: ActorSystem, ec: ExecutionContext, materializer: Materializer) {

  val executorConfig = Config.fromTypesafeConfig(config)
  implicit val clock = Clock.systemUTC()
  val kubernetesClient = skuber.k8sInit
  val k8sOperations = new K8sOperations(executorConfig, kubernetesClient)
  val executor = new KubernetesExecutor(executorConfig, k8sOperations)
  val serverConfig = ServerConfig.fromTypesafe(config)
  val server = new ExecutorServer(serverConfig, executor)

  server.start()

  def shutdown(): Unit = {
    server.stop()
    executor.shutdown()
    kubernetesClient.close
  }
}

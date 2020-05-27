package ai.mantik.executor.kubernetes

import java.time.Clock

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.ExecutorForIntegrationTest
import ai.mantik.executor.server.{ ExecutorServer, ServerConfig }
import com.typesafe.config.{ Config => TypesafeConfig }

/** An embedded executor for integration tests. */
class KubernetesExecutorForIntegrationTests(config: TypesafeConfig)(implicit akkaRuntime: AkkaRuntime) extends ExecutorForIntegrationTest {

  val executorConfig = Config.fromTypesafeConfig(config)
  implicit val clock = Clock.systemUTC()
  import ai.mantik.componently.AkkaHelper._
  val kubernetesClient = skuber.k8sInit
  val k8sOperations = new K8sOperations(executorConfig, kubernetesClient)
  val executor = new KubernetesExecutor(executorConfig, k8sOperations)
  val serverConfig = ServerConfig.fromTypesafe(config)
  val server = new ExecutorServer(serverConfig, executor)

  override def start(): Unit = {
    server.start()
  }

  def stop(): Unit = {
    server.stop()
    kubernetesClient.close
  }

  override def scrap(): Unit = {
    val cleaner = new KubernetesCleaner(kubernetesClient, executorConfig)
    cleaner.deleteKubernetesContent()
  }
}

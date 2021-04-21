package ai.mantik.executor.kubernetes

import java.time.Clock

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.ExecutorForIntegrationTest
import com.typesafe.config.{Config => TypesafeConfig}

/** An embedded executor for integration tests. */
class KubernetesExecutorForIntegrationTests(config: TypesafeConfig)(implicit akkaRuntime: AkkaRuntime)
    extends ExecutorForIntegrationTest {

  val executorConfig = Config.fromTypesafeConfig(config)
  implicit val clock = Clock.systemUTC()
  import ai.mantik.componently.AkkaHelper._
  val kubernetesClient = skuber.k8sInit
  val k8sOperations = new K8sOperations(executorConfig, kubernetesClient)
  val executor = new KubernetesExecutor(executorConfig, k8sOperations)

  override def start(): Unit = {}

  def stop(): Unit = {
    kubernetesClient.close
  }

  override def scrap(): Unit = {
    val cleaner = new KubernetesCleaner(kubernetesClient, executorConfig)
    cleaner.deleteKubernetesContent()
  }
}

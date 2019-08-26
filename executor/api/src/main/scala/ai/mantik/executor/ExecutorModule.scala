package ai.mantik.executor

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.ConfigurableDependencies

class ExecutorModule(implicit akkaRuntime: AkkaRuntime) extends ConfigurableDependencies {

  override protected val configKey: String = "mantik.executor.type"

  private val kubernetesType = "kubernetes"
  private val dockerType = "docker"
  private val clientType = "client"

  override protected def variants: Seq[Classes] = Seq(
    variation[Executor](
      kubernetesType -> provider("ai.mantik.executor.kubernetes.KubernetesExecutorProvider", asSingleton = true),
      clientType -> provider("ai.mantik.executor.client.ExecutorClientProvider", asSingleton = true),
      dockerType -> provider("ai.mantik.executor.docker.DockerExecutorProvider", asSingleton = true)
    )
  )
}

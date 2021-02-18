package ai.mantik.executor

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.ConfigurableDependencies

class ExecutorFileStorageModule(implicit akkaRuntime: AkkaRuntime) extends ConfigurableDependencies {
  override protected val configKey: String = "mantik.executor.storageType"

  private val s3Type = "s3"

  override protected def variants: Seq[Classes] = Seq(
    variation[ExecutorFileStorage](
      s3Type -> "ai.mantik.executor.s3storage.S3Storage"
    )
  )
}

package ai.mantik.planner.repository

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.ConfigurableDependencies
import ai.mantik.planner.repository.impl.{ LocalFileRepository, LocalRepository, TempFileRepository, TempRepository }

class RepositoryModule(implicit akkaRuntime: AkkaRuntime) extends ConfigurableDependencies {

  override protected val configKey: String = "mantik.repository.type"

  val TempVariant = "temp"
  val LocalVariant = "local"

  override protected def variants: Seq[Classes] = Seq(
    variation[Repository](
      TempVariant -> classOf[TempRepository],
      LocalVariant -> classOf[LocalRepository]
    ),
    variation[FileRepository](
      TempVariant -> classOf[TempFileRepository],
      LocalVariant -> classOf[LocalFileRepository]
    )
  )
}

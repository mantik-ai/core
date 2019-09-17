package ai.mantik.engine.testutil

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.planner.bridge.BridgesProvider
import ai.mantik.planner.impl.{ CachedFiles, PlannerImpl }
import ai.mantik.planner.repository.impl.{ LocalMantikRegistryImpl, MantikArtifactRetrieverImpl, TempFileRepository, TempRepository }
import ai.mantik.planner.repository.{ FileRepository, MantikArtifactRetriever, RemoteMantikRegistry, Repository }
import ai.mantik.planner.{ CacheKey, CoreComponents, Plan, PlanExecutor, Planner }
import org.apache.commons.io.FileUtils

import scala.concurrent.Future

class DummyComponents(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with CoreComponents {

  lazy val fileRepository = new TempFileRepository()
  lazy val repository: Repository = new TempRepository()
  override lazy val localRegistry = new LocalMantikRegistryImpl(fileRepository, repository)
  private lazy val registry: RemoteMantikRegistry = RemoteMantikRegistry.empty

  override def retriever: MantikArtifactRetriever = new MantikArtifactRetrieverImpl(
    localRegistry, registry
  )

  private val bridges = new BridgesProvider(config).get()
  override lazy val planner: Planner = new PlannerImpl(bridges, CachedFiles.empty)

  var nextItemToReturnByExecutor: Future[_] = Future.failed(
    new RuntimeException("Plan executor not implemented")
  )
  var lastPlan: Plan[_] = null

  override lazy val planExecutor: PlanExecutor = {
    new PlanExecutor {
      override def execute[T](plan: Plan[T]): Future[T] = {
        lastPlan = plan
        nextItemToReturnByExecutor.asInstanceOf[Future[T]]
      }

      override private[mantik] def cachedFile(cacheKey: CacheKey): Option[String] = None
    }
  }

  addShutdownHook {
    FileUtils.deleteDirectory(fileRepository.directory.toFile)
    Future.successful(())
  }

  /** Create a shared copy, which doesn't shut down on shutdown() */
  def shared(): CoreComponents = this
  // TODO: Remove me
}

package ai.mantik.engine.testutil

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.planner.bridge.BridgesProvider
import ai.mantik.planner.impl.PlannerImpl
import ai.mantik.planner.repository.impl.{ MantikArtifactRetrieverImpl, TempFileRepository, TempRepository }
import ai.mantik.planner.repository.{ FileRepository, MantikArtifactRetriever, MantikRegistry, Repository }
import ai.mantik.planner.{ CoreComponents, Plan, PlanExecutor, Planner }
import org.apache.commons.io.FileUtils

import scala.concurrent.Future

class DummyComponents(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with CoreComponents {

  override lazy val fileRepository = new TempFileRepository()

  override lazy val repository: Repository = new TempRepository()

  private lazy val registry: MantikRegistry = MantikRegistry.empty

  override def retriever: MantikArtifactRetriever = new MantikArtifactRetrieverImpl(
    repository, fileRepository, registry
  )

  private val bridges = new BridgesProvider(config).get()
  override lazy val planner: Planner = new PlannerImpl(bridges)

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
    }
  }

  override def shutdown(): Unit = {
    FileUtils.deleteDirectory(fileRepository.directory.toFile)
    fileRepository.shutdown()
  }

  /** Create a shared copy, which doesn't shut down on shutdown() */
  def shared(): CoreComponents = {
    val me = this
    new CoreComponents {
      override def fileRepository: FileRepository = me.fileRepository

      override def repository: Repository = me.repository

      override def retriever: MantikArtifactRetriever = me.retriever

      override def planner: Planner = me.planner

      override def planExecutor: PlanExecutor = me.planExecutor

      override def shutdown(): Unit = {}
    }
  }
}

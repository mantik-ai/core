package ai.mantik.engine.testutil

import java.time.Clock

import ai.mantik.planner.repository.{ FileRepository, Repository }
import ai.mantik.planner.{ CoreComponents, Plan, PlanExecutor, Planner }
import ai.mantik.planner.repository.impl.{ LocalFileRepository, LocalRepository }
import ai.mantik.planner.utils.{ AkkaRuntime, ComponentBase }
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.apache.commons.io.FileUtils

import scala.concurrent.Future

class DummyComponents(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with CoreComponents {

  override lazy val fileRepository = LocalFileRepository.createTemporary()

  override lazy val repository: Repository = LocalRepository.createTemporary()

  override lazy val planner: Planner = Planner.create(config)

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

      override def planner: Planner = me.planner

      override def planExecutor: PlanExecutor = me.planExecutor

      override def shutdown(): Unit = {}
    }
  }
}

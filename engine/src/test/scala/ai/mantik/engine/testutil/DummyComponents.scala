package ai.mantik.engine.testutil

import java.time.Clock

import ai.mantik.planner.{ CoreComponents, Plan, PlanExecutor, Planner }
import ai.mantik.repository.impl.{ LocalFileRepository, LocalRepository }
import ai.mantik.repository.{ FileRepository, Repository }
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }
import org.apache.commons.io.FileUtils

import scala.concurrent.{ ExecutionContext, Future }

class DummyComponents(implicit ec: ExecutionContext, materializer: Materializer, actorSystem: ActorSystem) extends CoreComponents {
  private lazy val config = ConfigFactory.load().withValue(
    // use a random port
    "mantik.repository.fileRepository.port", ConfigValueFactory.fromAnyRef(0)
  )

  override lazy val fileRepository = LocalFileRepository.createTemporary(config, Clock.systemUTC())

  override lazy val repository: Repository = LocalRepository.createTemporary(config)

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

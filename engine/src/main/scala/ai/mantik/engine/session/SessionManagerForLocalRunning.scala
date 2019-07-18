package ai.mantik.engine.session

import ai.mantik.componently.AkkaRuntime
import ai.mantik.planner.{ Context, CoreComponents, PlanExecutor, Planner }
import ai.mantik.planner.repository.{ FileRepository, Repository }
import javax.inject.{ Inject, Singleton }

@Singleton
class SessionManagerForLocalRunning @Inject() (context: Context)(implicit akkaRuntime: AkkaRuntime) extends SessionManager(
  id => {
    new Session(id, SessionManagerForLocalRunning.createViewForSession(context))
  }
)(akkaRuntime.executionContext)

object SessionManagerForLocalRunning {
  /** Create a view on to the context for a session (effectivly disabling the shutdown method). */
  private def createViewForSession(context: Context): CoreComponents = {
    new CoreComponents {
      override def fileRepository: FileRepository = context.fileRepository

      override def repository: Repository = context.repository

      override def planner: Planner = context.planner

      override def planExecutor: PlanExecutor = context.planExecutor

      override def shutdown(): Unit = {} // disabled
    }
  }
}

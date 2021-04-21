package ai.mantik.engine.session

import ai.mantik.componently.AkkaRuntime
import ai.mantik.planner.{PlanningContext, CoreComponents, PlanExecutor, Planner}
import ai.mantik.planner.repository.{FileRepository, MantikArtifactRetriever, Repository}
import javax.inject.{Inject, Singleton}

@Singleton
class SessionManagerForLocalRunning @Inject() (coreComponents: CoreComponents)(implicit akkaRuntime: AkkaRuntime)
    extends SessionManager(id => {
      // Note: We could override the quitSession method
      // if the session should do some cleanup here.
      new Session(id, SessionManagerForLocalRunning.createViewForSession(coreComponents))
    })(akkaRuntime.executionContext)

object SessionManagerForLocalRunning {

  /**
    * Create a view on to the context for a session.
    * Here we could create special views for the session.
    */
  private def createViewForSession(coreComponents: CoreComponents): CoreComponents = coreComponents
}

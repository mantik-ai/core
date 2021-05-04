/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
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

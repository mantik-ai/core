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
package ai.mantik.engine.integration

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.di.AkkaModule
import ai.mantik.engine.server.{EngineServer, ServiceModule}
import ai.mantik.engine.{EngineClient, EngineModule}
import ai.mantik.planner.PlanningContext
import ai.mantik.testutils.{AkkaSupport, TestBase}
import com.google.inject.Guice
import com.typesafe.config.{Config, ConfigFactory}

/** Base classes for integration tests. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport {

  protected var context: PlanningContext = _
  protected var engineServer: EngineServer = _
  protected var engineClient: EngineClient = _

  override protected lazy val typesafeConfig: Config = ConfigFactory.load("systemtest.conf")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    implicit val akkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)
    val injector = Guice.createInjector(
      new AkkaModule(),
      ServiceModule,
      new EngineModule()
    )
    engineServer = injector.getInstance(classOf[EngineServer])
    engineServer.start()
    engineClient = new EngineClient(s"localhost:${engineServer.port}")
    context = engineClient.planningContext
  }
}

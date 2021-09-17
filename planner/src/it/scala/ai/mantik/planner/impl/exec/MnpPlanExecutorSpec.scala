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
package ai.mantik.planner.impl.exec

import ai.mantik.ds.element.TabularBundle
import ai.mantik.elements.errors.MantikException
import ai.mantik.executor.model.ListWorkerRequest
import ai.mantik.planner.DataSet
import ai.mantik.planner.impl.PlanningContextImpl
import ai.mantik.planner.integration.IntegrationTestBase

class MnpPlanExecutorSpec extends IntegrationTestBase {
  trait Env {
    val contextImpl = context.asInstanceOf[PlanningContextImpl]
    val mnpExecutor = contextImpl.planExecutor.asInstanceOf[MnpPlanExecutor]
    val executor = contextImpl.executor

    val simpleAction = DataSet
      .literal(
        TabularBundle.buildColumnWise.withPrimitives("x", 1, 2, 3).result
      )
      .select("SELECT (x + 1) AS y")
      .fetch
    val simplePlan = contextImpl.planner.convert(simpleAction)

    def executeSimplePlan(): Unit = {
      await(mnpExecutor.execute(simplePlan)) shouldBe TabularBundle.buildColumnWise.withPrimitives("y", 2, 3, 4).result
    }
  }

  it should "execute a simple plan and update metrics" in new Env {
    contextImpl.metrics.workersCreated.getCount shouldBe 0
    contextImpl.metrics.mnpConnectionsCreated.getCount shouldBe 0
    contextImpl.metrics.mnpConnections.getCount shouldBe 0
    contextImpl.metrics.workers.getCount shouldBe 0
    executeSimplePlan()
    contextImpl.metrics.workersCreated.getCount shouldBe 1
    contextImpl.metrics.mnpConnectionsCreated.getCount shouldBe 1
    withClue("Connections should be closed after run") {
      contextImpl.metrics.mnpConnections.getCount shouldBe 0
    }
    eventually {
      withClue("Workers should be stopped") {
        contextImpl.metrics.workers.getCount shouldBe 0
      }
    }
  }

  it should "remove containers after successful evaluation" in new Env {
    val runningBefore = await(executor.listWorkers(ListWorkerRequest()))
    executeSimplePlan()
    eventually {
      val runningAfterwards = await(executor.listWorkers(ListWorkerRequest()))
      val delta = runningAfterwards.workers.diff(runningBefore.workers)
      delta shouldBe empty
    }
  }

  it should "remove containers after failed evaluation" in new Env {
    /*
    Casting to invalid types should let a container crash.
     */
    val crashing = DataSet
      .literal(
        TabularBundle.buildColumnWise.withPrimitives("x", "1", "2", "Bad").result
      )
      .select("SELECT CAST(x AS INT32)")
      .fetch

    val runningBefore = await(executor.listWorkers(ListWorkerRequest()))
    intercept[MantikException] {
      crashing.run()
    }
    eventually {
      val runningAfterwards = await(executor.listWorkers(ListWorkerRequest()))
      val delta = runningAfterwards.workers.diff(runningBefore.workers)
      delta shouldBe empty
    }
  }
}

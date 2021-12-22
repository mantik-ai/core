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

import ai.mantik.componently.AkkaRuntime
import ai.mantik.ds.FundamentalType
import ai.mantik.elements.{DataSetDefinition, ItemId, MantikHeader}
import ai.mantik.executor.Executor
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model._
import ai.mantik.mnp.MnpClient
import ai.mantik.planner.repository.impl.TempRepository
import ai.mantik.planner.repository.{DeploymentInfo, MantikArtifact}
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.Instants
import akka.actor.Cancellable
import akka.stream.KillSwitch
import akka.stream.scaladsl.Source
import com.typesafe.config.{Config, ConfigValueFactory}

import scala.concurrent.Future

class ExecutorMock(implicit val akkaRuntime: AkkaRuntime) extends Executor {
  override def nameAndVersion: Future[String] = ???

  override def startEvaluation(
      evaluationWorkloadRequest: EvaluationWorkloadRequest
  ): Future[EvaluationWorkloadResponse] = ???

  override def getEvaluation(id: String): Future[EvaluationWorkloadState] = ???

  override def trackEvaluation(id: String): Source[EvaluationWorkloadState, Cancellable] = ???

  override def cancelEvaluation(id: String): Future[Unit] = {
    toStop += id
    Future.successful(())
  }

  override def removeDeployment(id: String): Future[Unit] = {
    toStop += id
    Future.successful(())
  }

  override def list(): Future[ListResponse] = Future.successful(listResponse)

  var listResponse: ListResponse = _

  var toStop = Seq.newBuilder[String]
}

class ExecutionCleanupSpec extends TestBaseWithAkkaRuntime {
  trait Env {

    val pending = Seq(
      // Note: content except NodeName is not of importance for this test
      ListElement(
        id = "id1",
        status = WorkloadStatus.Running,
        kind = ListElementKind.Evaluation
      ),
      ListElement(
        id = "id2",
        status = WorkloadStatus.Running,
        kind = ListElementKind.Evaluation
      )
    )
    lazy val configOverride: Config => Config = identity
    lazy val runtimeOverride = akkaRuntime.withConfigOverride(configOverride)
    lazy val executorMock = new ExecutorMock()
    executorMock.listResponse = ListResponse(pending)
    lazy val repo = new TempRepository()
    lazy val cleanup = new ExecutionCleanup(executorMock, repo)(runtimeOverride)
  }

  it should "be disabled by default" in new Env {
    cleanup.isEnabled shouldBe false
    withClue("It should not fail") {
      await(cleanup.isReady)
    }
    executorMock.toStop.result() shouldBe empty
  }

  it should "remove pending workers" in new Env {
    override lazy val configOverride = { config =>
      config.withValue("mantik.planner.cleanupOnStart", ConfigValueFactory.fromAnyRef(true))
    }
    cleanup.isEnabled shouldBe true
    await(cleanup.isReady)
    executorMock.toStop.result() should contain theSameElementsAs Seq("id1", "id2")
  }

  it should "not remove them, if they are used by deployed items" in new Env {
    await(
      repo.store(
        MantikArtifact(
          mantikHeader = MantikHeader
            .pure(
              DataSetDefinition("a", FundamentalType.Int32)
            )
            .toJson,
          fileId = None,
          namedId = None,
          itemId = ItemId.generate(),
          deploymentInfo = Some(
            DeploymentInfo(
              evaluationId = "id2",
              internalUrl = "",
              externalUrl = None,
              timestamp = Instants.makeInstant(0)
            )
          ),
          executorStorageId = None
        )
      )
    )

    override lazy val configOverride = { config =>
      config.withValue("mantik.planner.cleanupOnStart", ConfigValueFactory.fromAnyRef(true))
    }
    cleanup.isEnabled shouldBe true
    await(cleanup.isReady)

    executorMock.toStop.result() should contain theSameElementsAs Seq(
      "id1"
    )
  }
}

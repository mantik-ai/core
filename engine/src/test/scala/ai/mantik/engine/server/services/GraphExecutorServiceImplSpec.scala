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
package ai.mantik.engine.server.services

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.functional.FunctionType
import ai.mantik.elements.{AlgorithmDefinition, ItemId, MantikHeader, NamedMantikId}
import ai.mantik.engine.protos.ds.BundleEncoding
import ai.mantik.engine.protos.graph_executor.{
  DeployItemRequest,
  FetchItemRequest,
  SaveItemRequest,
  ActionMeta => RpcActionMeta
}
import ai.mantik.engine.testutil.{TestArtifacts, TestBaseWithSessions}
import ai.mantik.planner.impl.MantikItemStateManager
import ai.mantik.planner.repository.MantikArtifact
import ai.mantik.planner.{ActionMeta, Algorithm, DataSet, DeploymentState, MantikItem, Pipeline, PlanOp, Source}

import scala.concurrent.Future

class GraphExecutorServiceImplSpec extends TestBaseWithSessions {

  trait Env {
    val graphExecutor = new GraphExecutorServiceImpl(sessionManager)
  }

  private val rpcActionMeta = Some(RpcActionMeta(name = "nice request"))
  private val actionMeta = ActionMeta(name = Some("nice request"))

  for {
    encoding <- Seq(BundleEncoding.ENCODING_MSG_PACK, BundleEncoding.ENCODING_JSON)
  } {
    it should s"fetch datasets with ${encoding}" in new Env {
      val lit = Bundle.fundamental("Hello World")
      val session1 = await(sessionManager.create())
      val dataset = DataSet.literal(lit)
      val dataset1Id = session1.addItem(
        dataset
      )
      components.nextItemToReturnByExecutor = Future.successful(lit)
      val response = await(
        graphExecutor.fetchDataSet(
          FetchItemRequest(session1.id, dataset1Id, encoding, meta = rpcActionMeta)
        )
      )
      val bundleDecoded = Converters.decodeBundle(response.bundle.get)
      bundleDecoded shouldBe lit
      components.lastPlan shouldBe components.planner.convert(dataset.fetch, actionMeta)
    }
  }

  "save" should "save elements" in new Env {
    val lit = Bundle.fundamental("Hello World")
    val session1 = await(sessionManager.create())
    val dataset = DataSet.literal(lit)
    val dataset1Id = session1.addItem(
      dataset
    )
    components.nextItemToReturnByExecutor = Future.successful(())
    val response = await(
      graphExecutor.saveItem(
        SaveItemRequest(
          session1.id,
          dataset1Id,
          "foo1",
          meta = rpcActionMeta
        )
      )
    )
    response.name shouldBe "foo1"
    response.mantikItemId shouldNot be(empty)
    components.lastPlan shouldBe components.planner.convert(dataset.tag("foo1").save(), actionMeta)
  }

  "deploy" should "deploy elements" in new Env {
    val algorithm1Artifact = MantikArtifact(
      MantikHeader
        .pure(
          AlgorithmDefinition(
            bridge = TestArtifacts.algoBridge1.namedId.get,
            `type` = FunctionType(FundamentalType.Int32, FundamentalType.StringType)
          )
        )
        .toJson,
      fileId = Some("1236"),
      namedId = Some(NamedMantikId("Algorithm1")),
      itemId = ItemId.generate()
    )
    val algorithm1 = MantikItem
      .fromMantikArtifact(algorithm1Artifact, components.stateManger, Seq(TestArtifacts.algoBridge1))
      .asInstanceOf[Algorithm]
    val pipeline1 = Pipeline.build(
      algorithm1
    )
    val session1 = await(sessionManager.create())
    val pipeline1Id = session1.addItem(
      pipeline1
    )
    components.nextItemToReturnByExecutor = Future.successful(
      DeploymentState(
        evaluationId = "name1",
        internalUrl = "internalUrl1",
        externalUrl = Some("externalUrl1")
      )
    )
    val response = await(
      graphExecutor.deployItem(
        DeployItemRequest(
          session1.id,
          pipeline1Id,
          nameHint = "nameHint1",
          ingressName = "ingress1",
          meta = rpcActionMeta
        )
      )
    )
    response.name shouldBe "name1"
    response.internalUrl shouldBe "internalUrl1"
    response.externalUrl shouldBe "externalUrl1"
    components.lastPlan.name shouldBe actionMeta.name
    val ops = components.lastPlan.op.asInstanceOf[PlanOp.Sequential[_]].plans
    val deployOp = ops.collectFirst { case x: PlanOp.DeployPipeline =>
      x
    }.get
    deployOp.serviceNameHint shouldBe Some("nameHint1")
    deployOp.ingress shouldBe Some("ingress1")
  }
}

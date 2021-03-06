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

import java.nio.file.Paths
import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.elements.errors.{ErrorCodes, MantikException}
import ai.mantik.engine.protos.ds.BundleEncoding
import ai.mantik.engine.protos.graph_builder.{ApplyRequest, GetRequest, LiteralRequest}
import ai.mantik.engine.protos.graph_executor.FetchItemRequest
import ai.mantik.engine.protos.local_registry.ListArtifactsRequest
import ai.mantik.engine.protos.sessions.CreateSessionRequest
import ai.mantik.engine.server.services.Converters
import com.google.protobuf.empty.Empty

class HelloWorldSpec extends IntegrationTestBase {

  val sampleBridge = Paths.get("bridge/tf/saved_model")
  val sampleFile = Paths.get("bridge/tf/saved_model/test/resources/samples/double_multiply")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    context.pushLocalMantikItem(sampleBridge)
    context.pushLocalMantikItem(sampleFile)
  }

  it should "be possible to run a simple command" in {
    val response = engineClient.aboutService.version(Empty())
    response.version shouldNot be(empty)
  }

  it should "support a simple calculation" in {
    val session = engineClient.sessionService.createSession(CreateSessionRequest())
    val algorithm = engineClient.graphBuilder.get(GetRequest(sessionId = session.sessionId, name = "double_multiply"))
    val myBundle = ai.mantik.ds.element.TabularBundle.buildColumnWise
      .withPrimitives("x", 1.0, 2.0)
      .result
    val encodeBundle = Converters.encodeBundle(myBundle, BundleEncoding.ENCODING_JSON)
    val dataset = engineClient.graphBuilder.literal(
      LiteralRequest(
        sessionId = session.sessionId,
        bundle = Some(
          encodeBundle
        )
      )
    )
    val result = engineClient.graphBuilder.algorithmApply(
      ApplyRequest(sessionId = session.sessionId, datasetId = dataset.itemId, algorithmId = algorithm.itemId)
    )
    val evaluated = engineClient.graphExecutor.fetchDataSet(
      FetchItemRequest(
        sessionId = session.sessionId,
        datasetId = result.itemId,
        encoding = BundleEncoding.ENCODING_JSON
      )
    )
    val decoded = Converters.decodeBundle(evaluated.bundle.get)
    decoded shouldBe TabularBundle.buildColumnWise
      .withPrimitives("y", 2.0, 4.0)
      .result
  }

  it should "give access to a context" in {
    val context = engineClient.planningContext
    intercept[MantikException] {
      context.loadDataSet("not-existing")
    }.code.isA(ErrorCodes.MantikItemNotFound)
  }

  it should "provide access to local registry" in {
    await(engineClient.localRegistryService.listArtifacts(ListArtifactsRequest())).artifacts shouldNot be(empty)
  }
}

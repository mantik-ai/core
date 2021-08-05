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
package ai.mantik.executor.common.test.integration

import ai.mantik.executor.model.{ListWorkerRequest, MnpPipelineDefinition, StartWorkerRequest, WorkerType}
import ai.mantik.testutils.{HttpSupport, TestBase}
import akka.util.ByteString

trait DeployPipelineSpecBase {
  self: IntegrationBase with TestBase with HttpSupport =>

  val pipeDef =
    """
      |{
      |  "name": "my_pipeline",
      |  "steps": [],
      |  "inputType": "int32"
      |}
    """.stripMargin

  it should "allow deploying a pipeline" in withExecutor { executor =>
    // we deploy an empty pipeline here, as this tests do not have a docker container with real
    // bridge images here, only fake ones.
    // and the pipelines checks types.
    val parsedDef = io.circe.parser.parse(pipeDef).forceRight

    val pipelineRequest = StartWorkerRequest(
      id = "service1",
      nameHint = Some("my-service"),
      definition = MnpPipelineDefinition(
        definition = parsedDef
      ),
      ingressName = Some("ingress1")
    )

    val response = await(executor.startWorker(pipelineRequest))
    response.nodeName shouldNot be(empty)
    response.externalUrl shouldNot be(empty)

    eventually {
      val applyUrl = response.externalUrl.get + "/apply"
      val httpPostResponse = httpPost(applyUrl, "application/json", ByteString.fromString("100"))
      httpPostResponse shouldBe ByteString.fromString("100")
    }

    val listResponse = await(executor.listWorkers(ListWorkerRequest()))
    val pipe = listResponse.workers.find(_.`type` == WorkerType.MnpPipeline)
    pipe shouldBe defined
    pipe.get.nodeName shouldBe response.nodeName
    pipe.get.externalUrl shouldBe response.externalUrl
  }
}

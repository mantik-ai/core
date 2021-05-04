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
package ai.mantik.planner.integration

import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.ds.sql.Select
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.planner.{Algorithm, Pipeline}
import ai.mantik.testutils.HttpSupport
import akka.util.ByteString

class DeployPipelineSpec extends IntegrationTestBase with Samples with HttpSupport {

  it should "be possible to deploy a pipeline" in new EnvWithAlgorithm {

    val inputAdaptor = Select
      .parse(
        TabularData(
          "x" -> FundamentalType.Int32
        ),
        "select CAST(x as float64)"
      )
      .forceRight

    val outputAdapter =
      Select.parse(doubleMultiply.functionType.output.asInstanceOf[TabularData], "select CAST (y as int32)").forceRight

    val pipeline = Pipeline.build(
      Left(inputAdaptor),
      Right(doubleMultiply),
      Left(outputAdapter)
    )

    context.state(pipeline).deployment shouldBe empty
    context.state(doubleMultiply).deployment shouldBe empty

    val deploymentState = context.execute(pipeline.deploy(ingressName = Some("pipe1"), nameHint = Some("pipe1")))

    context.state(pipeline).deployment shouldBe Some(deploymentState)
    deploymentState.externalUrl shouldNot be(empty)

    // Sub algorithms are now also deployed
    context.state(doubleMultiply).deployment shouldBe 'defined

    context.state(pipeline).deployment.get.sub.size shouldBe 2 // SQLs are deployed as sub steps

    val applyUrl = s"${deploymentState.externalUrl.get}/apply"
    val sampleData = ByteString("[[4],[5]]")

    val response = eventually {
      httpPost(applyUrl, "application/json", sampleData)
    }
    val responseParsed = CirceJson.forceParseJson(response.utf8String)
    responseParsed shouldBe CirceJson.forceParseJson("[[8],[10]]")
  }
}

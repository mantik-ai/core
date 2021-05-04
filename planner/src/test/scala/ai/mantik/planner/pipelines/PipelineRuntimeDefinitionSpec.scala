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
package ai.mantik.planner.pipelines

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.testutils.TestBase

import io.circe.syntax._

class PipelineRuntimeDefinitionSpec extends TestBase {

  val sample =
    """
      |{
      |  "name": "My nice pipeline",
      |  "inputType": "int32",
      |  "steps": [
      |    {
      |      "url": "http://service1",
      |      "outputType": "float32"
      |    },
      |    {
      |      "url": "http://service2",
      |      "outputType": "string"
      |    }
      |  ]
      |}
    """.stripMargin

  it should "be compatible with golangs data type" in {
    val parsed = CirceJson.forceParseJson(sample).as[PipelineRuntimeDefinition].forceRight
    parsed shouldBe PipelineRuntimeDefinition(
      name = "My nice pipeline",
      inputType = FundamentalType.Int32,
      steps = Seq(
        PipelineRuntimeDefinition.Step(
          "http://service1",
          FundamentalType.Float32
        ),
        PipelineRuntimeDefinition.Step(
          "http://service2",
          FundamentalType.StringType
        )
      )
    )

    parsed.asJson.as[PipelineRuntimeDefinition].forceRight shouldBe parsed
  }
}

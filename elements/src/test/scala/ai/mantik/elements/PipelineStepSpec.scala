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
package ai.mantik.elements

import ai.mantik.testutils.TestBase
import io.circe.{Json, parser}
import io.circe.syntax._

class PipelineStepSpec extends TestBase {

  it should "parse algorithm steps well" in {
    val example =
      """
        |{
        |  "algorithm": "foo:123",
        |  "description": "What a nice algorithm",
        |  "metaVariables": [{
        |    "name": "foo",
        |    "value": 123
        |  }]
        |}
      """.stripMargin

    val parsed = parser.parse(example).forceRight.as[PipelineStep].forceRight
    parsed shouldBe PipelineStep.AlgorithmStep(
      description = Some("What a nice algorithm"),
      algorithm = "foo:123",
      metaVariables = Some(
        List(
          PipelineStep.MetaVariableSetting(
            "foo",
            Json.fromInt(123)
          )
        )
      )
    )
    parsed.asJson.as[PipelineStep].forceRight shouldBe parsed
  }

  it should "like missing values" in {
    val example =
      """
        |{
        |  "algorithm":"foo"
        |}
      """.stripMargin
    val parsed = parser
      .parse(example)
      .forceRight
      .as[PipelineStep]
      .forceRight
    parsed shouldBe PipelineStep.AlgorithmStep(
      algorithm = "foo"
    )
    parsed.asJson.as[PipelineStep].forceRight shouldBe parsed
  }

  it should "parse select steps well" in {
    val example =
      """
        |{
        |  "select": "select i",
        |  "description": "Nice"
        |}
      """.stripMargin
    val parsed = parser.parse(example).forceRight.as[PipelineStep].forceRight
    parsed shouldBe PipelineStep.SelectStep(
      description = Some("Nice"),
      select = "select i"
    )
    parsed.asJson.as[PipelineStep].forceRight shouldBe parsed
  }
}

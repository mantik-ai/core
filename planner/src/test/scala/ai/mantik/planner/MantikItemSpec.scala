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
package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{AlgorithmDefinition, ItemId, MantikHeader, NamedMantikId}
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.util.FakeBridges
import ai.mantik.testutils.TestBase
import io.circe.syntax._

class MantikItemSpec extends TestBase {
  import MantikItemSpec.sample

  "MantikItem" should "be JSON serializable" in {
    val asJson = (MantikItemSpec.sample: MantikItem).asJson
    val back = asJson.as[MantikItem]
    back.forceRight.asJson shouldBe asJson
    val wanted = Right(MantikItemSpec.sample)
    back shouldBe wanted
    // More Tests are in MantikItemCodecSpec
  }

  "withMetaVariable" should "update meta variables" in {
    sample.mantikHeader.metaJson.metaVariable("x").get.value shouldBe Bundle.fundamental(123)
    sample.mantikId shouldBe an[ItemId]

    val after = sample.withMetaValue("x", 100)
    after.mantikHeader.metaJson.metaVariable("x").get.value shouldBe Bundle.fundamental(100)
    after.payloadSource shouldBe sample.payloadSource
    after.source.definition shouldBe an[DefinitionSource.Derived]
    after.mantikHeader.definition shouldBe an[AlgorithmDefinition]
    after.mantikId shouldBe an[ItemId]
    after.itemId shouldNot be(sample.itemId)
  }
}

object MantikItemSpec extends FakeBridges {
  lazy val sample = Algorithm(
    Source.constructed(PayloadSource.Loaded("1", ContentTypes.ZipFileContentType)),
    MantikHeader
      .fromYaml(
        """
          |bridge: algo1
          |metaVariables:
          |  - name: x
          |    value: 123
          |    type: int32
          |type:
          |  input: int32
          |  output: float32
      """.stripMargin
      )
      .right
      .getOrElse(???)
      .cast[AlgorithmDefinition]
      .getOrElse(???),
    algoBridge
  )
}

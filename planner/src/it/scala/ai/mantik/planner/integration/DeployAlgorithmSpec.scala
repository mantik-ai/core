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

import ai.mantik.ds.sql.Select
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.planner.{Algorithm, Pipeline}

class DeployAlgorithmSpec extends IntegrationTestBase with Samples {

  it should "deploy a simple bridged algorithm" in new EnvWithAlgorithm {
    context.state(doubleMultiply).itemStored shouldBe true
    context.state(doubleMultiply).deployment shouldBe empty
    val result = context.execute(
      doubleMultiply.deploy()
    )
    result.name shouldNot be(empty)
    result.internalUrl shouldNot be(empty)
    context.state(doubleMultiply).deployment shouldBe Some(result)

    withClue("Deploying again will lead to a no op and same result") {
      val result2 = context.execute(doubleMultiply.deploy())
      result2 shouldBe result
    }
  }

  it should "accept a service name hint" in new EnvWithAlgorithm {
    context.state(doubleMultiply).deployment shouldBe empty
    val result = context.execute(doubleMultiply.deploy(nameHint = Some("coolname")))
    result.internalUrl should include("coolname")
    result.name should include("coolname")
    context.state(doubleMultiply).deployment shouldBe Some(result)
  }

  it should "deploy a simple select algorithm" in {
    // Note: Selects are not part of Algorithm anymore
    val pipeline = Pipeline.build(
      Left(
        Select
          .parse(
            TabularData(
              "x" -> FundamentalType.Int32
            ),
            "select * where x = 10"
          )
          .forceRight
      )
    )
    val state = context.state(pipeline)
    state.deployment shouldBe empty
    state.itemStored shouldBe false
    val deployed = context.execute(
      pipeline.deploy()
    )
    val state2 = context.state(pipeline)
    state2.deployment shouldBe Some(deployed)
    state2.itemStored shouldBe true
  }

  it should "deploy a derived algorithm" in new EnvWithTrainedAlgorithm {
    val state = context.state(trained)
    state.itemStored shouldBe false
    state.deployment shouldBe None
    val result = context.execute(trained.deploy())
    result.name shouldNot be(empty)
    result.internalUrl shouldNot be(empty)
    val state2 = context.state(trained)
    state2.itemStored shouldBe true
    state2.deployment shouldBe Some(result)
  }

  it should "store the deployment state in database" in new EnvWithAlgorithm {
    val state = context.state(doubleMultiply)
    state.deployment shouldBe None
    val deployed = context.execute(doubleMultiply.deploy())
    val state2 = context.state(doubleMultiply)
    state2.deployment shouldBe Some(deployed)

    val loadAgain = context.loadAlgorithm(doubleMultiply.mantikId)
    val state3 = context.state(loadAgain)
    state3.deployment shouldBe Some(deployed)
  }
}

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

class LearnCachingSpec extends IntegrationTestBase with Samples {

  it should "cache results of learning automatically" in new EnvWithTrainedAlgorithm {
    trained.isCached shouldBe true
    trained.state.isCacheEvaluated shouldBe false

    trained.tag("algo1").save().run()

    trained.isCached shouldBe true
    trained.state.isCacheEvaluated shouldBe true

    val applied = trained.apply(learningData).fetch.run()
  }

  it should "make cache files persistent when needed" in new EnvWithTrainedAlgorithm {
    stats.isCached shouldBe true
    stats.state.isCacheEvaluated shouldBe false
    stats.fetch.run()
    stats.state.isCacheEvaluated shouldBe true
    val cacheFile = stats.state.cacheFile.get
    await(fileRepository.requestFileGet(cacheFile)).isTemporary shouldBe true

    stats.save().run()
    context.state(stats).payloadFile.get shouldNot be(cacheFile)
    await(fileRepository.requestFileGet(context.state(stats).payloadFile.get)).isTemporary shouldBe false
  }
}

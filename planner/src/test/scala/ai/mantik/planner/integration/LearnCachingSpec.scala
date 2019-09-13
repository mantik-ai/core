package ai.mantik.planner.integration

import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class LearnCachingSpec extends IntegrationTestBase with Samples {

  it should "cache results of learning automatically" in new EnvWithTrainedAlgorithm {
    MantikItemAnalyzer(trained).isCached shouldBe true
    MantikItemAnalyzer(trained).isCacheEvaluated shouldBe false

    trained.tag("algo1").save().run()

    MantikItemAnalyzer(trained).isCached shouldBe true
    MantikItemAnalyzer(trained).isCacheEvaluated shouldBe true

    val applied = trained.apply(learningData).fetch.run()
  }

  it should "make cache files persistent when needed" in new EnvWithTrainedAlgorithm {
    MantikItemAnalyzer(stats).isCacheEvaluated shouldBe false
    stats.fetch.run()
    MantikItemAnalyzer(stats).isCacheEvaluated shouldBe true
    val cacheFile = MantikItemAnalyzer(stats).cacheFile.get
    await(fileRepository.requestFileGet(cacheFile)).isTemporary shouldBe true

    stats.save().run()
    stats.state.get.payloadFile.get shouldNot be(cacheFile)
    await(fileRepository.requestFileGet(stats.state.get.payloadFile.get)).isTemporary shouldBe false
  }
}

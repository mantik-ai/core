package ai.mantik.planner.integration

import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class LearnCachingSpec extends IntegrationTestBase with Samples {

  it should "cache results of learning automatically" in new EnvWithTrainedAlgorithm {
    println(trained)
    MantikItemAnalyzer(trained).isCached shouldBe true
    MantikItemAnalyzer(trained).isCacheEvaluated shouldBe false

    trained.tag("algo1").save().run()

    MantikItemAnalyzer(trained).isCached shouldBe true
    MantikItemAnalyzer(trained).isCacheEvaluated shouldBe true

    val applied = trained.apply(learningData).fetch.run()
  }
}

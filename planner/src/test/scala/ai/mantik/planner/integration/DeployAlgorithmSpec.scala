package ai.mantik.planner.integration

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.planner.Algorithm
import ai.mantik.planner.select.Select
import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class DeployAlgorithmSpec extends IntegrationTestBase with Samples {

  it should "deploy a simple bridged algorithm" in new EnvWithAlgorithm {
    doubleMultiply.state.get.isStored shouldBe true
    doubleMultiply.state.get.deployment shouldBe empty
    val result = context.execute(
      doubleMultiply.deploy()
    )
    result.name shouldNot be(empty)
    result.url shouldNot be(empty)
    doubleMultiply.state.get.deployment shouldBe Some(result)

    withClue("Deploying again will lead to a no op and same result") {
      val result2 = context.execute(doubleMultiply.deploy())
      result2 shouldBe result
    }
  }

  it should "accept a service name hint" in new EnvWithAlgorithm {
    doubleMultiply.state.get.deployment shouldBe empty
    val result = context.execute(doubleMultiply.deploy(name = Some("coolname")))
    result.url should include("coolname")
    result.name should include("coolname")
    doubleMultiply.state.get.deployment shouldBe Some(result)
  }

  it should "deploy a simple select algorithm" in {
    val select = Algorithm.fromSelect(Select.parse(
      TabularData(
        "x" -> FundamentalType.Int32
      ),
      "select * where x = 10"
    ).forceRight)
    select.state.get.deployment shouldBe empty
    select.state.get.isStored shouldBe false
    val deployed = context.execute(
      select.deploy()
    )
    select.state.get.deployment shouldBe Some(deployed)
    select.state.get.isStored shouldBe true
  }

  it should "deploy a derived algorithm" in new EnvWithTrainedAlgorithm {
    trained.state.get.isStored shouldBe false
    trained.state.get.deployment shouldBe None
    val result = context.execute(trained.deploy())
    result.name shouldNot be(empty)
    result.url shouldNot be(empty)
    trained.state.get.isStored shouldBe true
    trained.state.get.deployment shouldBe Some(result)
  }

  it should "store the deployment state in database" in new EnvWithAlgorithm {
    doubleMultiply.state.get.deployment shouldBe None
    val deployed = context.execute(doubleMultiply.deploy())
    doubleMultiply.state.get.deployment shouldBe Some(deployed)

    val loadAgain = context.loadAlgorithm(doubleMultiply.mantikId)
    loadAgain.state.get.deployment shouldBe Some(deployed)
  }
}

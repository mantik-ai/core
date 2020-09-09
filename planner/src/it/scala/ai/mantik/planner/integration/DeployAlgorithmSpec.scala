package ai.mantik.planner.integration

import ai.mantik.ds.sql.Select
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.planner.Algorithm

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
    val select = Algorithm.fromSelect(Select.parse(
      TabularData(
        "x" -> FundamentalType.Int32
      ),
      "select * where x = 10"
    ).forceRight)
    val state = context.state(select)
    state.deployment shouldBe empty
    state.itemStored shouldBe false
    val deployed = context.execute(
      select.deploy()
    )
    val state2 = context.state(select)
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

package ai.mantik.planner.integration

class HelloDataSetSpec extends IntegrationTestBase with Samples {

  it should "read datasets" in new EnvWithDataSet {
    val content = mnistTrain.fetch.run()
    content.rows shouldNot be(empty)
  }

  it should "read datasets through selects" in new EnvWithDataSet {
    val a = mnistTrain.select("select x as y")
    val b = a.select("select y as z")
    val c = b.select("select z as a")
    val content = c.fetch.run()
    content.rows shouldNot be(empty)
  }
}

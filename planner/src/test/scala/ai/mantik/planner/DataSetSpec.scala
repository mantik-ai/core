package ai.mantik.planner

import ai.mantik.ds.element.Bundle
import ai.mantik.testutils.TestBase

class DataSetSpec extends TestBase {

  val sample = DataSet.literal(
    Bundle.fundamental(100)
  )

  "cached" should "return a cached variant of the DataSet" in {
    val cached = sample.cached
    val cachedSource = cached.payloadSource.asInstanceOf[PayloadSource.Cached]
    cachedSource.source shouldBe sample.payloadSource
    cached.mantikfile shouldBe sample.mantikfile
  }

  it should "do nothing, if it is already cached" in {
    val cached = sample.cached
    val cached2 = cached.cached
    cached2 shouldBe cached
  }
}

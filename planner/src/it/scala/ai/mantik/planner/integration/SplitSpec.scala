package ai.mantik.planner.integration

import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.ds.element.{Primitive, TabularBundle, TabularRow}
import ai.mantik.planner.DataSet

class SplitSpec extends IntegrationTestBase {

  val sample = TabularBundle(
    TabularData(
      "x" -> FundamentalType.Int32,
      "name" -> FundamentalType.StringType
    ),
    (for (i <- 0 until 100) yield {
      TabularRow(Primitive(i), Primitive(s"Element ${i}"))
    }).toVector
  )

  it should "it should be possible to split a dataset" in {
    val dataset = DataSet.literal(sample)

    val Seq(a, b, c) = dataset.split(Seq(0.5, 0.2))

    MantikItemAnalyzer(b).isCached shouldBe true
    MantikItemAnalyzer(b).isCacheEvaluated shouldBe false

    a.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(0, 50)
    )

    MantikItemAnalyzer(b).isCacheEvaluated shouldBe true

    b.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(50, 70)
    )
    c.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(70, 100)
    )
  }

  it should "be possible to shuffle" in {
    val dataset = DataSet.literal(sample)

    val Seq(a, b) = dataset.split(Seq(0.5), shuffleSeed = Some(1))
    val aFetched = a.fetch.run()
    aFetched.rows.size shouldBe 50
    aFetched.rows shouldNot be(sample.rows.slice(0, 50))

    val bFetched = b.fetch.run()
    bFetched.rows.size shouldBe 50
    bFetched.rows shouldNot be(sample.rows.slice(50, 100))

    val allRows = aFetched.rows.map(_.asInstanceOf[TabularRow]) ++
      bFetched.rows.map(_.asInstanceOf[TabularRow])

    TabularBundle(
      sample.model,
      allRows
    ).sorted shouldBe sample
  }

  it should "clamp values out of range" in {
    val dataset = DataSet.literal(sample)
    val Seq(a, b) = dataset.split(Seq(1.3))
    a.fetch.run().rows.size shouldBe 100
    b.fetch.run().rows.size shouldBe 0

    val Seq(c, d) = dataset.split(Seq(-0.4))
    c.fetch.run().rows.size shouldBe 0
    d.fetch.run().rows.size shouldBe 100
  }

  it should "support single split" in {
    val dataset = DataSet.literal(sample)
    val Seq(a) = dataset.split(Seq.empty, shuffleSeed = Some(2))
    val aResult = a.fetch.run()
    aResult.rows.size shouldBe 100
    val tabularRows = aResult.rows.map(_.asInstanceOf[TabularRow])
    tabularRows shouldNot be(sample.rows)
    TabularBundle(
      sample.model,
      tabularRows
    ).sorted shouldBe sample.sorted
  }

  it should "be possible to disable caching" in {
    val dataset = DataSet.literal(sample)

    val Seq(a, b) = dataset.split(Seq(0.5), cached = false)

    MantikItemAnalyzer(b).isCached shouldBe false
    MantikItemAnalyzer(b).isCacheEvaluated shouldBe false

    a.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(0, 50)
    )
    b.fetch.run() shouldBe sample.copy(
      rows = sample.rows.slice(50, 100)
    )

    MantikItemAnalyzer(b).isCacheEvaluated shouldBe false
  }
}

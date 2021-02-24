package ai.mantik.ds.sql

import ai.mantik.ds.element.TabularBundle
import ai.mantik.ds.testutil.TestBase

class SplitSpec extends TestBase {

  val input = TabularBundle
    .buildColumnWise
    .withPrimitives("x", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    .result

  def run(query: String): Vector[TabularBundle] = {
    implicit val context = SqlContext(Vector(input.model))
    val multiQuery = MultiQuery.parse(query).forceRight

    MultiQuery.parse(multiQuery.toStatement).forceRight shouldBe multiQuery

    multiQuery.run(input).forceRight
  }

  it should "with single case" in {
    run("SPLIT ($0) AT 0.5") shouldBe Vector(
      TabularBundle(
        input.model,
        rows = input.rows.take(5)
      ),
      TabularBundle(
        input.model,
        rows = input.rows.drop(5)
      )
    )
  }

  it should "work double case" in {
    run("SPLIT ($0) AT 0.5, 0.2") shouldBe Vector(
      TabularBundle(
        input.model,
        rows = input.rows.take(5)
      ),
      TabularBundle(
        input.model,
        rows = input.rows.slice(5, 7)
      ),
      TabularBundle(
        input.model,
        rows = input.rows.slice(7, 10)
      )
    )
  }

  it should "work with embedded select" in {
    run("SPLIT (SELECT x FROM $0 WHERE x = 5) AT 0.5") shouldBe Vector(
      TabularBundle(
        input.model,
        rows = Vector.empty
      ),
      TabularBundle(
        input.model,
        rows = input.rows.slice(4, 5)
      )
    )
  }

  it should "shuffle" in {
    val plain = run("SPLIT ($0) AT 0.7")
    plain.flatMap(_.rows) shouldBe input.rows

    val result = run("SPLIT ($0) AT 0.7 WITH SHUFFLE 1")
    result.size shouldBe 2
    result.map(_.model).distinct shouldBe Seq(input.model)
    result.map(_.rows.size) shouldBe Seq(7, 3)
    result shouldNot be(plain)
    result.flatMap(_.rows) should contain theSameElementsAs input.rows

    val result2 = run("SPLIT ($0) AT 0.7 WITH SHUFFLE 2")
    result2 shouldNot be(result)
    result2.flatMap(_.rows) should contain theSameElementsAs input.rows
  }

}

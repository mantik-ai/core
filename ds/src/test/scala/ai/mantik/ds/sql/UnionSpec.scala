package ai.mantik.ds.sql

import ai.mantik.ds.{FundamentalType, Nullable, TabularData}
import ai.mantik.ds.element.{Bundle, NullElement, TabularBundle}
import ai.mantik.ds.sql.builder.QueryBuilder
import ai.mantik.ds.testutil.TestBase

class UnionSpec extends TestBase {

  def autoUnion(
    a: TabularBundle,
    b: TabularBundle,
    all: Boolean
  ): TabularBundle = {
    val union = AutoUnion.autoUnion(a.model, b.model, all).forceRight
    testReencoding(union, a, b)
    union.run(a, b).forceRight
  }

  def sql(
    statement: String,
    data: TabularBundle*
  ): TabularBundle = {
    implicit val context = SqlContext(
      anonymous = data.map(_.model).toVector
    )
    val query = QueryBuilder.buildQuery(statement).forceRight
    testReencoding(query, data: _*)
    query.run(data: _*).forceRight
  }
  
  private def testReencoding(query: Query, data: TabularBundle*): Unit = {
    implicit val context = SqlContext(
      anonymous = data.map(_.model).toVector
    )
    val statement = query.toStatement
    val parsedAgain = QueryBuilder.buildQuery(statement).forceRight
    parsedAgain shouldBe query
  }

  val bundle1 = TabularBundle.build(
    TabularData(
      "x" -> FundamentalType.Int32,
      "y" -> FundamentalType.StringType,
    )
  ).row(1, "Hello")
    .row(2, "World")
    .result

  val bundle2 = TabularBundle.build(
    TabularData(
      "x" -> FundamentalType.Int32,
      "z" -> FundamentalType.Float32
    ))
    .row(1, 3.14f)
    .row(2, 6.12f)
    .result

  val bundle3 = TabularBundle.build(
    bundle1.model
  ).row(1, "Hello")
    .row(3, "Do you hear me")
    .result

  it should "run simple unions" in {
    Union(
      AnonymousInput(bundle1.model),
      AnonymousInput(bundle3.model, 1),
      all = true
    ).run(bundle1, bundle3).forceRight shouldBe TabularBundle.build(
      bundle1.model
    ) .row(1, "Hello")
      .row(2, "World")
      .row(1, "Hello")
      .row(3, "Do you hear me")
      .result

    Union(
      AnonymousInput(bundle1.model),
      AnonymousInput(bundle3.model, 1),
      all = false
    ).run(bundle1, bundle3).forceRight shouldBe TabularBundle.build(
      bundle1.model
    ) .row(1, "Hello")
      .row(2, "World")
      .row(3, "Do you hear me")
      .result
  }

  it should "run auto unions" in {
    autoUnion(bundle1, bundle2, true) shouldBe TabularBundle.build(
      TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> Nullable(FundamentalType.StringType),
        "z" -> Nullable(FundamentalType.Float32)
      )
    ).row(1, "Hello", NullElement)
      .row(2, "World", NullElement)
      .row(1, NullElement, 3.14f)
      .row(2, NullElement, 6.12f)
      .result

    autoUnion(bundle1, bundle1, true) shouldBe TabularBundle.build(
      bundle1.model
    ).row(1, "Hello")
      .row(2, "World")
      .row(1, "Hello")
      .row(2, "World")
      .result

    autoUnion(bundle1, bundle1, false) shouldBe bundle1
  }

  it should "drop duplicates" in {
    autoUnion(bundle1, bundle3, false) shouldBe TabularBundle.build(
      bundle1.model
    ).row(1, "Hello")
      .row(2, "World")
      .row(3, "Do you hear me")
      .result
  }

  it should "run complex select based unions" in {
    sql("SELECT y FROM $0 WHERE x = 1 UNION SELECT y FROM $1 WHERE x = 3", bundle1, bundle3) shouldBe TabularBundle.build(
      TabularData(
        "y" -> FundamentalType.StringType
      )
    ).row("Hello")
      .row("Do you hear me")
      .result
  }

  it should "allow multiple parameters" in {
    sql("SELECT x FROM $0 UNION SELECT x FROM $1 UNION SELECT x FROM $2", bundle1, bundle2, bundle3) shouldBe TabularBundle.build(
      TabularData(
        "x" -> FundamentalType.Int32
      )
    ).row(1)
      .row(2)
      .row(3)
      .result
  }
}

package ai.mantik.planner

import ai.mantik.ds.{ DataType, FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.elements.{ DataSetDefinition, ItemId, MantikHeader, NamedMantikId }
import ai.mantik.planner.impl.TestItems
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.select.AutoAdapt
import ai.mantik.testutils.TestBase

class DataSetSpec extends TestBase {

  val sample = DataSet.literal(
    Bundle.fundamental(100)
  )

  "cached" should "return a cached variant of the DataSet" in {
    val cached = sample.cached
    val cachedSource = cached.payloadSource.asInstanceOf[PayloadSource.Cached]
    cachedSource.source shouldBe sample.payloadSource
    cached.mantikHeader shouldBe sample.mantikHeader
  }

  it should "do nothing, if it is already cached" in {
    val cached = sample.cached
    val cached2 = cached.cached
    cached2 shouldBe cached
  }

  val type1 = TabularData(
    "x" -> FundamentalType.Int32
  )

  val type2 = TabularData(
    "y" -> FundamentalType.Int32
  )

  private def makeDs(dt: DataType): DataSet = {
    // DataSet source is not important here.
    DataSet(
      Source(DefinitionSource.Loaded(Some(NamedMantikId("item1234")), ItemId.generate()), PayloadSource.Loaded("someId", ContentTypes.ZipFileContentType)),
      MantikHeader.pure(DataSetDefinition(bridge = "someformat", `type` = dt)),
      TestItems.formatBridge
    )
  }

  "autoAdaptOrFail" should "not touch identical datasets" in {
    val ds1 = makeDs(type1)
    ds1.autoAdaptOrFail(type1) shouldBe ds1
  }

  it should "figure out figure out single columns" in {
    val ds1 = makeDs(type1)
    val adapted = ds1.autoAdaptOrFail(type2)
    adapted.dataType shouldBe type2
    adapted.payloadSource shouldBe an[PayloadSource.OperationResult]
  }

  it should "fail if there is no conversion" in {
    val ds1 = makeDs(type1)
    intercept[ConversionNotApplicableException] {
      ds1.autoAdaptOrFail(TabularData("z" -> FundamentalType.BoolType))
    }
  }
}

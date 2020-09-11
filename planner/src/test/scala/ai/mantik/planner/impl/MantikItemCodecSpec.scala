package ai.mantik.planner.impl

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.sql.Select
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.elements.{ AlgorithmDefinition, ItemId, MantikHeader }
import ai.mantik.planner.impl.TestItems.algoBridge
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner._
import ai.mantik.testutils.TestBase
import io.circe.syntax._
import io.circe.{ Decoder, Encoder }

class MantikItemCodecSpec extends TestBase {

  implicit val encoder: Encoder[MantikItem] = MantikItemCodec
  implicit val decoder: Decoder[MantikItem] = MantikItemCodec

  private def serializationTest(item: MantikItem): MantikItem = {
    val serialized = item.asJson
    println(s"Serialized ${item.getClass.getSimpleName}: ${serialized.spaces2}")
    val back = serialized.as[MantikItem]
    back shouldBe Right(item)
    back.forceRight
  }

  "algorithms" should "be serialized" in {
    serializationTest(
      Algorithm(
        Source(DefinitionSource.Loaded(Some("algo1:version1"), ItemId.generate()), PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)), TestItems.algorithm1,
        TestItems.algoBridge
      )
    )
  }

  it should "serialize selects" in {
    serializationTest(
      Algorithm.fromSelect(
        Select.parse(
          TabularData(
            "x" -> FundamentalType.Int32
          ),
          "select x as y"
        ).forceRight
      )
    )
  }

  it should "work with meta variables" in {
    MantikItemSpec.sample.mantikHeader.metaJson.metaVariables shouldNot be(empty)
    serializationTest(
      MantikItemSpec.sample
    )
  }

  "datasets" should "be serialized" in {
    serializationTest(
      DataSet(
        Source(DefinitionSource.Loaded(Some("dataset1:version1"), ItemId.generate()), PayloadSource.Loaded("dataset1", ContentTypes.MantikBundleContentType)), TestItems.dataSet1,
        TestItems.formatBridge
      )
    )
  }

  it should "select primitive values" in {
    serializationTest(
      DataSet.literal(Bundle.fundamental(5))
    )
  }

  it should "serialize random itemIds" in {
    val bundle = Bundle.buildColumnWise
      .withPrimitives("x", 1, 2, 3)
      .withPrimitives("s", "Hello", "World", "How")
      .result
    val dataset = DataSet.literal(bundle)
    val itemId = dataset.itemId
    val got = serializationTest(dataset)
    got.itemId shouldBe itemId
  }

  "trainable algorithms" should "be serialized" in {
    serializationTest(
      TrainableAlgorithm(
        MantikItemCore(
          Source(
            DefinitionSource.Loaded(Some("mantikId1"), ItemId.generate()),
            PayloadSource.Loaded("file1", "application/zip")
          ),
          TestItems.learning1,
          TestItems.learningBridge
        ),
        TestItems.learningBridge
      )
    )
  }

  "bridges" should "be serialized" in {
    serializationTest(
      TestItems.learningBridge
    )
  }

  "pipelines" should "be serialized" in {
    val out1 = TabularData(
      "y" -> FundamentalType.Int32
    )
    val pipeline = Pipeline.build(
      Algorithm.fromSelect(
        Select.parse(
          TabularData(
            "x" -> FundamentalType.Int32
          ),
          "select x as y"
        ).forceRight
      ),
      Algorithm(
        Source(DefinitionSource.Loaded(Some("algo1:version1"), ItemId.generate()), PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)),
        MantikHeader.pure(
          AlgorithmDefinition(
            bridge = algoBridge.mantikId,
            `type` = FunctionType(
              input = out1,
              output = FundamentalType.StringType
            )
          )),
        TestItems.algoBridge
      )
    )

    serializationTest(pipeline)
  }
}

package ai.mantik.planner.integration

import ai.mantik.ds.FundamentalType.Float64
import ai.mantik.ds.Tensor
import ai.mantik.ds.element.{Bundle, TabularBundle, TensorElement}
import ai.mantik.planner.{DataSet, Pipeline}

class AnonymousPipelineSpec extends IntegrationTestBase with Samples {

  def makeTensor(a: Double, b: Double): TensorElement[Double] = TensorElement(IndexedSeq(a, b))

  it should "save and load a pipeline with anonymous elements in it" in new EnvWithBridges {
    context.pushLocalMantikItem(kmeansDirectory)

    val learningData = TabularBundle.buildColumnWise
      .withColumn(
        "coordinates",
        Tensor(componentType = Float64, shape = List(2)),
        IndexedSeq(
          makeTensor(1.0, 2.0),
          makeTensor(2.0, 3.0),
          makeTensor(0.0, 4.0),
          makeTensor(2.0, 3.0)
        )
      )
      .result

    val kmeans = context.loadTrainableAlgorithm("kmeans")

    // kmeans will end up as an anonymous algorithm inside the pipeline.

    val (trained, _) = kmeans.train(DataSet.literal(learningData))

    val pipeline = Pipeline.build(trained).tag("pipeline1234")
    context.execute(pipeline.save())

    val sampleData = TabularBundle
      .build(learningData.model)
      .row(makeTensor(1.0, 2.0))
      .row(makeTensor(0.0, 3.0))
      .result

    val pipeline2 = context.loadPipeline("pipeline1234")
    val used = context.execute(pipeline2.apply(DataSet.literal(sampleData)).fetch).asInstanceOf[TabularBundle]

    used.rows.size shouldBe 2
    used.model.lookupColumnIndex("label") shouldBe Some(0)
  }
}

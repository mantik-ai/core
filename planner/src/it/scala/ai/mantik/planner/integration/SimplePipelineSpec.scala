package ai.mantik.planner.integration

import ai.mantik.ds.TabularData
import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.ds.sql.Select
import ai.mantik.planner.{Algorithm, DataSet, Pipeline}

class SimplePipelineSpec extends IntegrationTestBase with Samples {

  trait Env extends EnvWithBridges {
    context.pushLocalMantikItem(doubleMultiplyDirectory)

    val doubleMultiply = context.loadAlgorithm("double_multiply")
    val toStringConversion =
      Select.parse(doubleMultiply.functionType.output.asInstanceOf[TabularData], "select CAST (y as int32) AS z").right.get


    val pipeline = Pipeline.build(
      Right(doubleMultiply),
      Left(toStringConversion)
    )

    val input = TabularBundle.buildColumnWise
      .withPrimitives("x", 1.0, 2.0)
      .result

    val inputDataSet = DataSet.literal(input)

    val expectedOutput = TabularBundle.buildColumnWise
      .withPrimitives("z", 2, 4)
      .result
  }

  it should "construct and execute simple pipelines" in new Env {
    val applied = pipeline.apply(inputDataSet)
    val got = context.execute(applied.fetch)

    got shouldBe expectedOutput
  }

  it should "be possible to save and restore a pipeline" in new Env {
    context.execute(pipeline.tag("pipeline1").save())
    val loadedAgain = context.loadPipeline("pipeline1")

    val applied = loadedAgain.apply(inputDataSet)
    val got = context.execute(applied.fetch)

    got shouldBe expectedOutput
  }
}

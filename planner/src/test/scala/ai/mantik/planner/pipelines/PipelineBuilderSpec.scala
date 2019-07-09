package ai.mantik.planner.pipelines

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.elements
import ai.mantik.elements.PipelineStep.{ AlgorithmStep, SelectStep }
import ai.mantik.elements.{ AlgorithmDefinition, ItemId, Mantikfile, PipelineStep }
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.select.Select
import ai.mantik.planner.{ Algorithm, DefinitionSource, PayloadSource, Source }
import ai.mantik.testutils.TestBase

class PipelineBuilderSpec extends TestBase {

  val algorithm1 = Algorithm(
    source = Source(DefinitionSource.Loaded("algo1", ItemId.generate()), PayloadSource.Loaded("file1", ContentTypes.MantikBundleContentType)),
    Mantikfile.pure(
      AlgorithmDefinition(
        stack = "stack1",
        `type` = FunctionType(
          input = TabularData("x" -> FundamentalType.Int32),
          output = TabularData("y" -> FundamentalType.StringType)
        ),
        directory = Some("dir1")
      )
    )
  )

  val algorithm2 = Algorithm(
    source = Source.constructed(PayloadSource.Loaded("file2", ContentTypes.MantikBundleContentType)),
    Mantikfile.pure(
      elements.AlgorithmDefinition(
        stack = "stack1",
        `type` = FunctionType(
          input = TabularData("y" -> FundamentalType.StringType),
          output = TabularData("z" -> FundamentalType.Float64)
        ),
        directory = Some("dir1")
      )
    )
  )

  val selectAlgorithm = Algorithm.fromSelect(
    Select.parse(
      TabularData(
        "x" -> FundamentalType.Int32
      ), "select cast (x as string) as y"
    ).forceRight
  )

  it should "build pipelines" in {
    val pipeline = PipelineBuilder.build(Seq(algorithm1, algorithm2)).forceRight
    pipeline.definitionSource shouldBe DefinitionSource.Constructed()
    pipeline.payloadSource shouldBe PayloadSource.Empty
    pipeline.resolved.steps.map(_.algorithm) shouldBe Seq(algorithm1, algorithm2)

    withClue("Algorithms which are loaded are using their real ids") {
      val algo1 = pipeline.resolved.steps.head
      algo1.pipelineStep.asInstanceOf[PipelineStep.AlgorithmStep].algorithm shouldNot be('anonymous)
      val algo2 = pipeline.resolved.steps(1)
      algo2.pipelineStep.asInstanceOf[PipelineStep.AlgorithmStep].algorithm shouldBe 'anonymous

      pipeline.mantikfile.definition.referencedItems.size shouldBe 2
    }
  }

  it should "insert select steps, if possible" in {
    val pipeline = PipelineBuilder.build(Seq(selectAlgorithm, algorithm2)).forceRight
    val step1 = pipeline.resolved.steps.head
    val encodedStep = step1.pipelineStep.asInstanceOf[SelectStep]
    encodedStep.select shouldBe selectAlgorithm.select.get.toSelectStatement
    val step2 = pipeline.resolved.steps(1)
    step2.pipelineStep shouldBe an[AlgorithmStep]

    pipeline.mantikfile.definition.referencedItems.size shouldBe 1 // select is not referenced
  }
}

package ai.mantik.planner.pipelines

import ai.mantik.ds.{ DataType, FundamentalType, TabularData, Tensor }
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.planner.{ Algorithm, DefinitionSource, PayloadSource, Source }
import ai.mantik.repository.PipelineStep.MetaVariableSetting
import ai.mantik.repository.{ AlgorithmDefinition, ContentTypes, MantikId, Mantikfile, OptionalFunctionType, PipelineDefinition, PipelineStep }
import ai.mantik.testutils.TestBase
import io.circe.Json

class PipelineResolverSpec extends TestBase {

  val algorithm1 = Algorithm(
    source = Source.constructed(PayloadSource.Loaded("file1", ContentTypes.MantikBundleContentType)),
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
      AlgorithmDefinition(
        stack = "stack1",
        `type` = FunctionType(
          input = TabularData("y" -> FundamentalType.StringType),
          output = TabularData("z" -> FundamentalType.Float64)
        ),
        directory = Some("dir1")
      )
    )
  )

  private def resolvePipeline2(
    a: Algorithm, b: Algorithm,
    inputType: Option[DataType] = None,
    outputType: Option[DataType] = None
  ): Either[PipelineException, ResolvedPipeline] = {
    PipelineResolver.resolvePipeline(
      Mantikfile.pure(PipelineDefinition(
        steps = List(
          PipelineStep.AlgorithmStep("a"),
          PipelineStep.AlgorithmStep("b")
        ),
        `type` = Some(
          OptionalFunctionType(
            input = inputType,
            output = outputType
          )
        )
      )), Map(
        MantikId("a") -> a,
        MantikId("b") -> b
      )
    )
  }

  private def resolvePipeline2Select(
    a: String,
    b: Algorithm,
    inputType: Option[DataType] = None,
    outputType: Option[DataType] = None
  ): Either[PipelineException, ResolvedPipeline] = {
    PipelineResolver.resolvePipeline(
      Mantikfile.pure(PipelineDefinition(
        steps = List(
          PipelineStep.SelectStep(a),
          PipelineStep.AlgorithmStep("b")
        ),
        `type` = Some(
          OptionalFunctionType(
            input = inputType,
            output = outputType
          )
        )
      )), Map(
        MantikId("b") -> b
      )
    )
  }

  it should "deny empty pipelines" in {
    PipelineResolver.resolvePipeline(
      Mantikfile.pure(PipelineDefinition(
        steps = Nil
      )), Map.empty
    ).forceLeft.getMessage should include("Empty Pipeline")
  }

  it should "resolve a simple pipeline" in {
    val resolved = resolvePipeline2(algorithm1, algorithm2).forceRight
    resolved.steps shouldBe List(
      ResolvedPipelineStep(PipelineStep.AlgorithmStep("a"), algorithm1),
      ResolvedPipelineStep(PipelineStep.AlgorithmStep("b"), algorithm2)
    )
    resolved.functionType shouldBe FunctionType(
      input = algorithm1.functionType.input,
      output = algorithm2.functionType.output
    )
  }

  it should "figure out if types are not matching" in {
    val resolved = resolvePipeline2(algorithm2, algorithm1)
    resolved.forceLeft.getMessage should include("Type mismatch")
  }

  it should "validate input/output types if present" in {
    val resolved = resolvePipeline2(algorithm1, algorithm2, inputType = Some(algorithm1.functionType.input))
    resolved shouldBe 'right
    val resolved2 = resolvePipeline2(algorithm1, algorithm2, outputType = Some(algorithm2.functionType.output))
    resolved2 shouldBe 'right

    val badInput = resolvePipeline2(algorithm1, algorithm2, inputType = Some(FundamentalType.Int32))
    badInput.forceLeft shouldBe an[PipelineTypeException]

    val badOutput = resolvePipeline2(algorithm1, algorithm2, outputType = Some(FundamentalType.Int32))
    badOutput.forceLeft shouldBe an[PipelineTypeException]
  }

  it should "allow select statements if input type is present" in {
    val resolved = resolvePipeline2Select("select cast (x as string) as y", algorithm2, inputType = Some(algorithm1.functionType.input))
    resolved shouldBe 'right
    val missingInput = resolvePipeline2Select("select cast (x as string) as y", algorithm2, inputType = None)
    missingInput.forceLeft shouldBe an[InvalidPipelineException]
    val badType = resolvePipeline2Select("select cast (x as string) as y", algorithm2, inputType = Some(
      TabularData(
        "foo" -> FundamentalType.Int32
      )
    ))
    badType.forceLeft shouldBe an[InvalidPipelineException]
  }

  val algorithm3 = Algorithm(
    source = Source.constructed(PayloadSource.Loaded("file2", ContentTypes.MantikBundleContentType)),
    Mantikfile.fromYaml(
      """kind: algorithm
        |stack: foo
        |metaVariables:
        |  - name: out
        |    type: int32
        |    value: 2
        |type:
        |  input:
        |    columns:
        |      y: string
        |  output:
        |    columns:
        |      z:
        |        type: tensor
        |        componentType: float32
        |        shape: ["${out}"]
      """.stripMargin
    ).forceRight.cast[AlgorithmDefinition].forceRight
  )

  it should "apply meta variables to algorithms" in {
    val resolved = resolvePipeline2(algorithm1, algorithm3).forceRight
    resolved.functionType.output shouldBe TabularData("z" -> Tensor(FundamentalType.Float32, List(2)))

    // no change in algorithm, so still constructed.
    resolved.steps(1).algorithm.source.definition shouldBe an[DefinitionSource.Constructed]

    val resolvedWithApplication = PipelineResolver.resolvePipeline(
      Mantikfile.pure(PipelineDefinition(
        steps = List(
          PipelineStep.AlgorithmStep("a"),
          PipelineStep.AlgorithmStep("b", metaVariables = Some(
            List(
              MetaVariableSetting("out", Json.fromInt(10))
            )
          ))
        )
      )), Map(
        MantikId("a") -> algorithm1,
        MantikId("b") -> algorithm3
      )
    ).forceRight
    resolvedWithApplication.functionType.output shouldBe TabularData("z" -> Tensor(FundamentalType.Float32, List(10)))
    resolvedWithApplication.steps(1).algorithm.source.definition shouldBe an[DefinitionSource.Derived]

  }

}

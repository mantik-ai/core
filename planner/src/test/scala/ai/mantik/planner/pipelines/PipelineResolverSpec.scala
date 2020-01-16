package ai.mantik.planner.pipelines

import ai.mantik.ds.{ DataType, FundamentalType, TabularData, Tensor }
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements
import ai.mantik.elements.PipelineStep.MetaVariableSetting
import ai.mantik.elements.{ AlgorithmDefinition, MantikHeader, NamedMantikId, OptionalFunctionType, PipelineDefinition, PipelineStep }
import ai.mantik.planner.impl.TestItems
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.{ Algorithm, DefinitionSource, PayloadSource, Source }
import ai.mantik.testutils.TestBase
import io.circe.Json

class PipelineResolverSpec extends TestBase {

  val algorithm1 = Algorithm(
    source = Source.constructed(PayloadSource.Loaded("file1", ContentTypes.MantikBundleContentType)),
    MantikHeader.pure(
      AlgorithmDefinition(
        bridge = TestItems.algoBridge.mantikId,
        `type` = FunctionType(
          input = TabularData("x" -> FundamentalType.Int32),
          output = TabularData("y" -> FundamentalType.StringType)
        )
      )
    ),
    TestItems.algoBridge
  )

  val algorithm2 = Algorithm(
    source = Source.constructed(PayloadSource.Loaded("file2", ContentTypes.MantikBundleContentType)),
    MantikHeader.pure(
      elements.AlgorithmDefinition(
        bridge = TestItems.algoBridge.mantikId,
        `type` = FunctionType(
          input = TabularData("y" -> FundamentalType.StringType),
          output = TabularData("z" -> FundamentalType.Float64)
        )
      )
    ),
    TestItems.algoBridge
  )

  private def resolvePipeline2(
    a: Algorithm, b: Algorithm,
    inputType: Option[DataType] = None,
    outputType: Option[DataType] = None
  ): Either[PipelineException, ResolvedPipeline] = {
    PipelineResolver.resolvePipeline(
      MantikHeader.pure(PipelineDefinition(
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
        NamedMantikId(name = "a") -> a,
        NamedMantikId(name = "b") -> b
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
      MantikHeader.pure(PipelineDefinition(
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
        NamedMantikId(name = "b") -> b
      )
    )
  }

  it should "deny empty pipelines" in {
    PipelineResolver.resolvePipeline(
      MantikHeader.pure(PipelineDefinition(
        steps = Nil
      )), Map.empty
    ).forceLeft.getMessage should include("Empty Pipeline")
  }

  it should "resolve a simple pipeline" in {
    val resolved = resolvePipeline2(algorithm1, algorithm2).forceRight
    resolved.steps shouldBe List(
      algorithm1,
      algorithm2
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
    MantikHeader.fromYaml(
      """kind: algorithm
        |bridge: bridge1
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
    ).forceRight.cast[AlgorithmDefinition].forceRight,
    TestItems.algoBridge
  )

  it should "apply meta variables to algorithms" in {
    val resolved = resolvePipeline2(algorithm1, algorithm3).forceRight
    resolved.functionType.output shouldBe TabularData("z" -> Tensor(FundamentalType.Float32, List(2)))

    // no change in algorithm, so still constructed.
    resolved.steps(1).source.definition shouldBe an[DefinitionSource.Constructed]

    val resolvedWithApplication = PipelineResolver.resolvePipeline(
      MantikHeader.pure(PipelineDefinition(
        steps = List(
          PipelineStep.AlgorithmStep("a"),
          PipelineStep.AlgorithmStep("b", metaVariables = Some(
            List(
              MetaVariableSetting("out", Json.fromInt(10))
            )
          ))
        )
      )), Map(
        NamedMantikId(name = "a") -> algorithm1,
        NamedMantikId(name = "b") -> algorithm3
      )
    ).forceRight
    resolvedWithApplication.functionType.output shouldBe TabularData("z" -> Tensor(FundamentalType.Float32, List(10)))
    resolvedWithApplication.steps(1).source.definition shouldBe an[DefinitionSource.Derived]

  }

}

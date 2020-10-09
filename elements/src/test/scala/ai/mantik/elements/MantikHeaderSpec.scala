package ai.mantik.elements

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements
import ai.mantik.elements.meta.MetaVariable
import ai.mantik.testutils.TestBase
import io.circe.Json

class MantikHeaderSpec extends TestBase {

  val sample =
    """author: John Doe
      |authorEmail: john.doe@example.com
      |name: super_duper_algorithm
      |version: "0.1"
      |bridge: tensorflow1.6
      |type:
      |  input: uint8
      |  output: string
    """.stripMargin

  val minimalFile =
    """name: yup
      |bridge: foobar
      |type:
      |   input: bool
      |   output: bool
    """.stripMargin

  it should "parse an easy MantikHeader" in {
    val mf = MantikHeader.fromYamlWithType[AlgorithmDefinition](sample).forceRight

    mf.definition shouldBe elements.AlgorithmDefinition(
      bridge = "tensorflow1.6",
      `type` = FunctionType(FundamentalType.Uint8, FundamentalType.StringType)
    )

    mf.header shouldBe MantikHeaderMeta(
      author = Some("John Doe"),
      authorEmail = Some("john.doe@example.com"),
      name = Some("super_duper_algorithm"),
      version = Some("0.1")
    )
    mf.violations shouldBe empty
  }

  it should "see invalid name/version" in {
    val sample =
      """name: InvalidName
        |version: Invalid-234
        |bridge: foo
        |type:
        |   input: bool
        |   output: bool
      """.stripMargin
    val pure = MantikHeader.fromYamlWithoutCheck(sample).forceRight
    pure.violations shouldBe Seq("Invalid Name", "Invalid Version")
    val errorMessage = MantikHeader.fromYamlWithType[AlgorithmDefinition](sample).left.get.getMessage
    errorMessage shouldBe "Invalid MantikHeader: Invalid Name,Invalid Version"
  }

  it should "parse a minimal file" in {
    val mf = MantikHeader.fromYamlWithType[AlgorithmDefinition](minimalFile).forceRight
    mf.definition shouldBe elements.AlgorithmDefinition(
      bridge = "foobar",
      `type` = FunctionType(FundamentalType.BoolType, FundamentalType.BoolType)
    )
    mf.header shouldBe MantikHeaderMeta(
      name = Some("yup")
    )
    mf.violations shouldBe empty
  }

  it should "convert to yaml and back" in {
    for {
      ymlCode <- Seq(sample, minimalFile)
    } {
      val parsed = MantikHeader.fromYaml(ymlCode).right.get
      val asYml = parsed.toYaml
      val parsedAgain = MantikHeader.fromYaml(asYml).right.get
      parsed shouldBe parsedAgain
    }
  }

  it should "have no problems with decimal version key" in {
    // this is a yaml (library?) problem, the 0.1 get's parsed as double 1e-1
    // and cannot be feed into the version field.
    // it must be escaped by `0.1`.
    pendingUntilFixed {
      val code =
        """name: Foo
          |version: 0.1
          |stack: bla
          |type
          |  input: int8
          |  output: int8
        """.stripMargin
      val mf = MantikHeader.fromYamlWithType[AlgorithmDefinition](code).forceRight
      mf.definition shouldBe elements.AlgorithmDefinition(
        bridge = "bla",
        `type` = FunctionType(FundamentalType.Uint8, FundamentalType.Uint8)
      )
      mf.header shouldBe MantikHeaderMeta(
        name = Some("Foo"),
        version = Some("0.1")
      )
    }
  }

  it should "parse dataset definitions" in {
    val definition =
      """
        |kind: dataset
        |name: dataset1
        |version: '0.1'
        |author: Superman
        |authorEmail: me@example.com
        |bridge: binary
        |type:
        |  columns:
        |    "x": int32
        |    "y": string
      """.stripMargin
    val mantikHeader = MantikHeader.fromYamlWithType[DataSetDefinition](definition).forceRight
    mantikHeader.definition shouldBe elements.DataSetDefinition(
      bridge = "binary",
      `type` = TabularData(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.StringType
      )
    )
    mantikHeader.header shouldBe MantikHeaderMeta(
      name = Some("dataset1"),
      version = Some("0.1"),
      author = Some("Superman"),
      authorEmail = Some("me@example.com")
    )
  }

  it should "parse trainable algorithms" in {
    val definition =
      """
        |kind: trainable
        |bridge: foo1
        |name: train1
        |type:
        | input:
        |   int32
        | output:
        |   int64
        |trainingType: int32
        |statType: string
      """.stripMargin
    val mantikHeader = MantikHeader.fromYamlWithType[TrainableAlgorithmDefinition](definition).forceRight
    mantikHeader.definition shouldBe elements.TrainableAlgorithmDefinition(
      bridge = "foo1",
      `type` = FunctionType(
        FundamentalType.Int32,
        FundamentalType.Int64
      ),
      trainingType = FundamentalType.Int32,
      statType = FundamentalType.StringType
    )
    mantikHeader.header shouldBe MantikHeaderMeta(
      name = Some("train1")
    )
  }

  it should "parse Pipelines" in {
    val definition =
      """kind: pipeline
        |name: pipeline1
        |steps:
        |  - algorithm: "algo1:v1"
        |    description: We like this step
        |  - algorithm: "algo2"
        |    metaVariables:
        |      - name: abc
        |        value: 123
        |  - select: "select i"
        |type:
        |  input:
        |    columns:
        |      x: int32
        |  output:
        |    columns:
        |      y: int32
      """.stripMargin
    val mantikHeader = MantikHeader.fromYamlWithType[PipelineDefinition](definition).forceRight
    mantikHeader.definition shouldBe
      PipelineDefinition(
        `type` = Some(OptionalFunctionType(
          input = Some(TabularData("x" -> FundamentalType.Int32)),
          output = Some(TabularData("y" -> FundamentalType.Int32))
        )),
        steps = List(
          PipelineStep.AlgorithmStep(
            description = Some("We like this step"),
            algorithm = "algo1:v1"
          ),
          PipelineStep.AlgorithmStep(
            algorithm = "algo2",
            metaVariables = Some(List(
              PipelineStep.MetaVariableSetting("abc", Json.fromInt(123))
            ))
          ),
          PipelineStep.SelectStep(
            "select i"
          )
        )
      )
    mantikHeader.header shouldBe MantikHeaderMeta(
      name = Some("pipeline1")
    )
    val asJson = mantikHeader.toJsonValue
    val parsedAgain = MantikHeader.parseSingleDefinition(asJson).right.getOrElse(fail)
    parsedAgain.definition shouldBe mantikHeader.definition
  }

  it should "contain the raw JSON" in {
    val definition =
      """
        |kind: dataset
        |name: dataset1
        |version: '0.1'
        |author: Superman
        |authorEmail: me@example.com
        |bridge: binary
        |unknown: must still be stored.
        |type: int32
      """.stripMargin
    val mantikHeader = MantikHeader.fromYaml(definition).right.get
    mantikHeader.toYaml should include("must still be stored")
  }

  it should "generate the trained variant from learnable Algorithms" in {
    val definition =
      """
        |kind: trainable
        |bridge: foo1
        |name: train1
        |version: "0.1"
        |type:
        | input:
        |   int32
        | output:
        |   int64
        |trainingType: int32
        |statType: string
      """.stripMargin
    val mantikHeader = MantikHeader.fromYaml(definition).right.get.cast[TrainableAlgorithmDefinition].right.get

    val casted = MantikHeader.generateTrainedMantikHeader(mantikHeader).right.get
    casted.definition shouldBe elements.AlgorithmDefinition(
      bridge = "foo1",
      `type` = FunctionType(
        FundamentalType.Int32,
        FundamentalType.Int64
      )
    )
  }

  it should "also support overrides for learnable algorithms" in {
    val definition =
      """
        |kind: trainable
        |bridge: foo1
        |trainedBridge: foo2
        |name: train1
        |version: "0.1"
        |type:
        | input:
        |   int32
        | output:
        |   int64
        |trainingType: int32
        |statType: string
      """.stripMargin
    val mantikHeader = MantikHeader.fromYaml(definition).right.get.cast[TrainableAlgorithmDefinition].right.get

    val casted = MantikHeader.generateTrainedMantikHeader(mantikHeader).right.get
    casted.definition shouldBe elements.AlgorithmDefinition(
      bridge = "foo2",
      `type` = FunctionType(
        FundamentalType.Int32,
        FundamentalType.Int64
      )
    )
  }

  it should "fix meta variables" in {
    val definition =
      """
        |kind: trainable
        |metaVariables:
        |  - name: abc
        |    type: int32
        |    value: 100
        |bridge: foo1
        |trainedBridge: foo2
        |type:
        |  input:
        |    type: tensor
        |    shape: ["${abc}"]
        |    componentType: float32
        |  output:
        |    type: tensor
        |    shape: [2, "${abc}"]
        |    componentType: float32
        |trainingType: int32
        |statType: string
      """.stripMargin
    val parsedDefinition = MantikHeader.fromYaml(definition)
    val mantikHeader = parsedDefinition.right.getOrElse(fail).cast[TrainableAlgorithmDefinition].right.get
    val casted = MantikHeader.generateTrainedMantikHeader(mantikHeader).right.get
    casted.metaJson.metaVariables shouldBe List(
      MetaVariable("abc", Bundle.fundamental(100), fix = true)
    )
    val expected =
      """
        |kind: algorithm
        |metaVariables:
        |  - name: abc
        |    type: int32
        |    value: 100
        |    fix: true
        |bridge: foo2
        |type:
        |  input:
        |    type: tensor
        |    shape: ["${abc}"]
        |    componentType: float32
        |  output:
        |    type: tensor
        |    shape: [2, "${abc}"]
        |    componentType: float32
        |trainingType: int32
        |statType: string
      """.stripMargin
    val parsedExpected = MantikHeader.fromYaml(expected).getOrElse(fail)
    casted.toJsonValue shouldBe parsedExpected.toJsonValue
    casted shouldBe parsedExpected
  }

  it should "encode transparently to JSON" in {
    val mf = MantikHeader.fromYamlWithType[AlgorithmDefinition](sample).forceRight
    import io.circe.syntax._
    val json = mf.asJson
    json shouldBe mf.toJsonValue
    val back = json.as[MantikHeader[AlgorithmDefinition]]
    back shouldBe Right(mf)
  }

  "bridges" should "have a default content type/version" in {
    val content =
      """kind: bridge
        |account: mantik
        |name: binary
        |suitable:
        |  - dataset
        |dockerImage: mantikai/bridge.binary
        |""".stripMargin
    val bridge = MantikHeader.fromYamlWithType[BridgeDefinition](content).forceRight
    bridge.definition.payloadContentType shouldBe Some("application/zip")
    bridge.definition.protocol shouldBe 1
  }

  it should "have a way to disable content type" in {
    val content =
      """kind: bridge
        |account: mantik
        |name: binary
        |suitable:
        |  - dataset
        |dockerImage: mantikai/bridge.binary
        |payloadContentType: null
        |""".stripMargin
    val bridge = MantikHeader.fromYamlWithType[BridgeDefinition](content).forceRight
    bridge.definition.payloadContentType shouldBe None
  }

  it should "it should be overridable" in {
    val content =
      """kind: bridge
        |account: mantik
        |name: binary
        |suitable:
        |  - dataset
        |dockerImage: mantikai/bridge.binary
        |payloadContentType: application/text
        |""".stripMargin
    val bridge = MantikHeader.fromYamlWithType[BridgeDefinition](content).forceRight
    bridge.definition.payloadContentType shouldBe Some("application/text")
  }

  "combiner" should "work" in {
    val content =
      """kind: combiner
        |bridge: foo
        |input:
        |  - int32
        |  - float32
        |output:
        |  - string
        |  - columns:
        |      x: int32
        |""".stripMargin
    val header = MantikHeader.fromYamlWithType[CombinerDefinition](content).forceRight
    header.definition.bridge shouldBe MantikId.fromString("foo")
    header.definition.input shouldBe Seq(
      FundamentalType.Int32,
      FundamentalType.Float32
    )
    header.definition.output shouldBe Seq(
      FundamentalType.StringType,
      TabularData(
        "x" -> FundamentalType.Int32
      )
    )
  }
}

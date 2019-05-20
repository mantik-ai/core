package ai.mantik.repository

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.testutils.TestBase

class MantikfileSpec extends TestBase {

  val sample =
    """author: John Doe
      |authorEmail: john.doe@example.com
      |name: super_duper_algorithm
      |version: "0.1"
      |stack: tensorflow1.6
      |directory: my_dir
      |type:
      |  input: uint8
      |  output: string
    """.stripMargin

  val minimalFile =
    """name: My Mini Algorithm
      |stack: foobar
      |directory: mydir
      |type:
      |   input: bool
      |   output: bool
    """.stripMargin

  it should "parse an easy Mantikfile" in {
    Mantikfile.fromYaml(sample).right.get.definitionAs[AlgorithmDefinition] shouldBe Right(
      AlgorithmDefinition(
        author = Some("John Doe"),
        authorEmail = Some("john.doe@example.com"),
        name = Some("super_duper_algorithm"),
        version = Some("0.1"),
        stack = "tensorflow1.6",
        directory = Some("my_dir"),
        `type` = FunctionType(FundamentalType.Uint8, FundamentalType.StringType)
      )
    )
  }

  it should "parse a minimal file" in {
    Mantikfile.fromYaml(minimalFile).right.get.definitionAs[AlgorithmDefinition] shouldBe Right(
      AlgorithmDefinition(
        name = Some("My Mini Algorithm"),
        stack = "foobar",
        directory = Some("mydir"),
        `type` = FunctionType(FundamentalType.BoolType, FundamentalType.BoolType)
      )
    )
  }

  it should "convert to yaml and back" in {
    for {
      ymlCode <- Seq(sample, minimalFile)
    } {
      val parsed = Mantikfile.fromYaml(ymlCode).right.get
      val asYml = parsed.toYaml
      val parsedAgain = Mantikfile.fromYaml(asYml).right.get
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
          |directory: dir
          |type
          |  input: int8
          |  output: int8
        """.stripMargin
      Mantikfile.fromYaml(code).right.get.definitionAs[AlgorithmDefinition].right.get shouldBe AlgorithmDefinition(
        name = Some("Foo"),
        version = Some("0.1"),
        stack = "bla",
        directory = Some("dir"),
        `type` = FunctionType(FundamentalType.Uint8, FundamentalType.Uint8)
      )
    }
  }

  it should "validate names in a second step" in {
    val mantikFile = Mantikfile.fromYaml(sample).right.get.definitionAs[AlgorithmDefinition].right.get
    mantikFile.name shouldBe Some("super_duper_algorithm")
    mantikFile.violations shouldBe empty

    val other =
      """name: Illegal Name
        |version: "0.1 ILLEGAL"
        |stack: bla
        |directory: dir
        |type:
        |  input: string
        |  output: string
      """.stripMargin
    val parsed = Mantikfile.fromYaml(other).right.get.definitionAs[AlgorithmDefinition].right.get
    parsed.name shouldBe Some("Illegal Name")
    parsed.version shouldBe Some("0.1 ILLEGAL")
    parsed.violations should contain theSameElementsAs Seq("Invalid Name", "Invalid Version")
  }

  it should "parse dataset definitions" in {
    val definition =
      """
        |kind: dataset
        |name: dataset1
        |version: '0.1'
        |author: Superman
        |authorEmail: me@example.com
        |directory: my_dir
        |format: binary
        |type:
        |  columns:
        |    "x": int32
        |    "y": string
      """.stripMargin
    val mantikfile = Mantikfile.fromYaml(definition).right.get
    mantikfile.definitionAs[DataSetDefinition] shouldBe Right(
      DataSetDefinition(
        name = Some("dataset1"),
        version = Some("0.1"),
        author = Some("Superman"),
        authorEmail = Some("me@example.com"),
        directory = Some("my_dir"),
        format = "binary",
        `type` = TabularData(
          "x" -> FundamentalType.Int32,
          "y" -> FundamentalType.StringType
        )
      )
    )
  }

  it should "parse trainable algorithms" in {
    val definition =
      """
        |kind: trainable
        |stack: foo1
        |name: train1
        |type:
        | input:
        |   int32
        | output:
        |   int64
        |trainingType: int32
        |statType: string
        |directory: foo
      """.stripMargin
    val mantikfile = Mantikfile.fromYaml(definition).right.get
    mantikfile.definitionAs[TrainableAlgorithmDefinition] shouldBe Right(
      TrainableAlgorithmDefinition(
        name = Some("train1"),
        stack = "foo1",
        `type` = FunctionType(
          FundamentalType.Int32,
          FundamentalType.Int64
        ),
        trainingType = FundamentalType.Int32,
        statType = FundamentalType.StringType,
        directory = Some("foo")
      )
    )
  }

  it should "contain the raw JSON" in {
    val definition =
      """
        |kind: dataset
        |name: dataset1
        |version: '0.1'
        |author: Superman
        |authorEmail: me@example.com
        |directory: my_dir
        |format: binary
        |unknown: must still be stored.
        |type: int32
      """.stripMargin
    val mantikfile = Mantikfile.fromYaml(definition).right.get
    mantikfile.toYaml should include("must still be stored")
  }

  it should "generate the trained variant from learnable Algorithms" in {
    val definition =
      """
        |kind: trainable
        |stack: foo1
        |name: train1
        |version: "0.1"
        |type:
        | input:
        |   int32
        | output:
        |   int64
        |trainingType: int32
        |statType: string
        |directory: foo
      """.stripMargin
    val mantikfile = Mantikfile.fromYaml(definition).right.get.cast[TrainableAlgorithmDefinition].right.get

    val casted = Mantikfile.generateTrainedMantikfile(mantikfile).right.get
    casted.definition shouldBe AlgorithmDefinition (
      stack = "foo1",
      `type` = FunctionType(
        FundamentalType.Int32,
        FundamentalType.Int64
      ),
      directory = Some("foo")
    )
  }

  it should "also support overrides for learnable algorithms" in {
    val definition =
      """
        |kind: trainable
        |stack: foo1
        |trainedStack: foo2
        |name: train1
        |version: "0.1"
        |type:
        | input:
        |   int32
        | output:
        |   int64
        |trainingType: int32
        |statType: string
        |directory: foo
      """.stripMargin
    val mantikfile = Mantikfile.fromYaml(definition).right.get.cast[TrainableAlgorithmDefinition].right.get

    val casted = Mantikfile.generateTrainedMantikfile(mantikfile).right.get
    casted.definition shouldBe AlgorithmDefinition (
      stack = "foo2",
      `type` = FunctionType(
        FundamentalType.Int32,
        FundamentalType.Int64
      ),
      directory = Some("foo")
    )
  }
}

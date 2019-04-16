package ai.mantik.planner.impl

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner._
import ai.mantik.repository._
import ai.mantik.testutils.TestBase
import cats.data.State
import io.circe.CursorOp.SetLefts

class PlannerImplSpec extends TestBase {

  private trait Env {
    val isolationSpace = "test"
    val planner = new PlannerImpl(TestItems.testBridges)

    def runWithEmptyState[X](f: => State[PlanningState, X]): (PlanningState, X) = {
      f.run(PlanningState()).value
    }
  }

  private val lit = Bundle.build(
    TabularData(
      "x" -> FundamentalType.Int32
    )
  )
    .row(1)
    .result

  "translateItemPayloadSource" should "not work on missing files" in new Env {
    intercept[Planner.NotAvailableException]{
      runWithEmptyState(planner.translateItemPayloadSource(
        Source.Empty
      ))
    }
  }

  it should "provide a file when there is a file source" in new Env {
    val (state, source) = runWithEmptyState(planner.translateItemPayloadSource(
      Source.Loaded("file1")
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), fileId = Some("file1"), read = true)
    )
    source shouldBe ResourcePlan(
      graph = Graph(
        nodes = Map (
          "1" -> Node (
            PlanNodeService.File(PlanFileReference(1)),
            resources = Map (
              ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source)
            )
          )
        )
      ),
      outputs = Seq(
        NodeResourceRef("1", ExecutorModelDefaults.SourceResource)
      )
    )
  }

  it should "convert a literal to a file and provide it as stream if given" in new Env {
    val (state, source) = runWithEmptyState(planner.translateItemPayloadSource(
      Source.BundleLiteral(lit)
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), write = true, read = true, temporary = true)
    )
    source shouldBe ResourcePlan(
      pre = PlanOp.PushBundle(lit, PlanFileReference(1)),
      graph = Graph(
        nodes = Map (
          "1" -> Node (
            PlanNodeService.File(PlanFileReference(1)),
            resources = Map (
              ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source)
            )
          )
        )
      ),
      outputs = Seq(
        NodeResourceRef("1", ExecutorModelDefaults.SourceResource)
      )
    )
  }

  it should "project results of operations" in new Env {
    val (state, source) = runWithEmptyState(planner.translateItemPayloadSource(
      Source.OperationResult(
        Operation.Application(
          Algorithm(Source.Loaded("algo1"), TestItems.algorithm1),
          DataSet(Source.Loaded("dataset1"), TestItems.dataSet1)
        )
      )
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("dataset1")),
      PlanFile(PlanFileReference(2), read = true, fileId = Some("algo1")),
    )
    source shouldBe ResourcePlan(
      graph = Graph(
        nodes = Map (
          "1" -> Node (
            PlanNodeService.File(PlanFileReference(1)),
            resources = Map (
              ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
            )
          ),
          "2" -> Node (
            PlanNodeService.DockerContainer(Container("algorithm1_image"), data = Some(PlanFileReference(2)), mantikfile = TestItems.algorithm1),
            resources = Map (
              ExecutorModelDefaults.TransformationResource -> NodeResource(ResourceType.Transformer, Some(ContentTypes.MantikBundleContentType))
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("1", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("2", ExecutorModelDefaults.TransformationResource)
        )
      ),
      outputs = Seq(
        NodeResourceRef("2", ExecutorModelDefaults.TransformationResource)
      )
    )
  }

  "translateItemPayloadSourceAsFile" should "not support empties" in new Env {
    intercept[Planner.NotAvailableException]{
      planner.translateItemPayloadSourceAsFile(Source.Empty, canBeTemporary = true)
    }
  }

  it should "convert a file load" in new Env {
    val (state, (op, file)) = runWithEmptyState(planner.translateItemPayloadSourceAsFile(Source.Loaded("file1"), canBeTemporary = true))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("file1"))
    )
    op shouldBe PlanOp.Empty
    file shouldBe PlanFileReference(1)
  }

  it should "convert a algorithm output" in new Env {
    for {
      temp <- Seq(false, true)
    } {
      val source = Source.OperationResult(
        Operation.Application(
          Algorithm(Source.Loaded("algo1"), TestItems.algorithm1),
          DataSet(Source.Loaded("dataset1"), TestItems.dataSet1)
        )
      )
      val (state, (op, file)) = runWithEmptyState(planner.translateItemPayloadSourceAsFile(
        source, canBeTemporary = temp
      ))
      state.files shouldBe List (
        PlanFile(PlanFileReference(1), read = true, fileId = Some("dataset1")),
        PlanFile(PlanFileReference(2), read = true, fileId = Some("algo1")),
        PlanFile(PlanFileReference(3), read = true, write = true, temporary = temp)
      )
      file shouldBe PlanFileReference(3)
      val expected = PlanOp.RunGraph(
        Graph(
          nodes = Map (
            // this part is taken over from stream generation
            "1" -> Node (
              PlanNodeService.File(PlanFileReference(1)),
              resources = Map (
                ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
              )
            ),
            "2" -> Node (
              PlanNodeService.DockerContainer(Container("algorithm1_image"), data = Some(PlanFileReference(2)), mantikfile = TestItems.algorithm1),
              resources = Map (
                ExecutorModelDefaults.TransformationResource -> NodeResource(ResourceType.Transformer, Some(ContentTypes.MantikBundleContentType))
              )
            ),
            // this part is used for generating the file
            "3" -> Node (
              PlanNodeService.File(PlanFileReference(3)),
              resources = Map (
                ExecutorModelDefaults.SinkResource -> NodeResource(ResourceType.Sink, Some(ContentTypes.MantikBundleContentType))
              )
            )
          ),
          links = Link.links(
            NodeResourceRef("1", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("2", ExecutorModelDefaults.TransformationResource),
            NodeResourceRef("2", ExecutorModelDefaults.TransformationResource) -> NodeResourceRef("3", ExecutorModelDefaults.SinkResource)
          )
        )
      )
      op shouldBe expected
    }
  }

  "translateItemPayloadSourceAsOptionalFile" should "support empty" in new Env {
    val (state, planOp) = runWithEmptyState(planner.translateItemPayloadSourceAsOptionalFile(
      Source.Empty, canBeTemporary = true
    ))
    state shouldBe PlanningState()
    planOp shouldBe (PlanOp.Empty, None)
  }

  "manifestDataSet" should "convert a simple literal source" in new Env {
    val sourcePlan = runWithEmptyState(planner.manifestDataSet(
      DataSet.natural(Source.BundleLiteral(lit), lit.model)
    ))._2

    sourcePlan.pre shouldBe PlanOp.PushBundle(lit, PlanFileReference(1))
    sourcePlan.graph shouldBe Graph(
      Map(
        "1" -> Node(
          PlanNodeService.File(PlanFileReference(1)),
          Map(ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType)))
        )
      )
    )
    sourcePlan.outputs shouldBe Seq(NodeResourceRef("1", ExecutorModelDefaults.SourceResource))
  }

  it should "convert a load natural source" in new Env {
    val ds = DataSet.natural(
      Source.Loaded(
        "file1"
      ),
      lit.model
    )

    val sourcePlan = runWithEmptyState(planner.manifestDataSet(
      ds
    ))._2

    sourcePlan.pre shouldBe PlanOp.Empty
    sourcePlan.graph shouldBe Graph(
      Map(
        "1" -> Node(
          PlanNodeService.File(PlanFileReference(1)),
          Map(
            ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
          )
        )
      )
    )
    sourcePlan.outputs shouldBe Seq(NodeResourceRef("1", ExecutorModelDefaults.SourceResource))
  }

  "manifestTrainableAlgorithm" should "manifest a trainable algorithm" in new Env {
    val (state, sourcePlan) = runWithEmptyState(planner.manifestTrainableAlgorithm(
      TrainableAlgorithm(Source.Loaded("file1"), TestItems.learning1)
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), read = true, fileId = Some("file1")))
    sourcePlan shouldBe ResourcePlan(
      graph = Graph(
        nodes = Map(
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("training1_image"), data = Some(PlanFileReference(1)), mantikfile = TestItems.learning1), resources = Map(
              "train" -> NodeResource(ResourceType.Sink, Some(ContentTypes.MantikBundleContentType)),
              "stats" -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType)),
              "result" -> NodeResource(ResourceType.Source, Some(ContentTypes.ZipFileContentType))
            )
          )
        )
      ),
      inputs = Seq(NodeResourceRef("1", "train")),
      outputs = Seq(
        NodeResourceRef("1", "result"),
        NodeResourceRef("1", "stats")
      )
    )
  }

  "manifestAlgorithm" should "manifest an algorithm" in new Env {
    val (state, sourcePlan) = runWithEmptyState(planner.manifestAlgorithm(
      Algorithm(Source.Loaded("file1"), TestItems.algorithm1)
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), read = true, fileId = Some("file1")))
    sourcePlan shouldBe ResourcePlan(
      graph = Graph(
        nodes = Map(
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("algorithm1_image"), data = Some(PlanFileReference(1)), mantikfile = TestItems.algorithm1), resources = Map(
              "apply" -> NodeResource(ResourceType.Transformer, Some(ContentTypes.MantikBundleContentType))
            )
          )
        )
      ),
      inputs = Seq(NodeResourceRef("1", "apply")),
      outputs = Seq(NodeResourceRef("1", "apply"))
    )
  }

  "convert" should "convert a simple save action" in new Env {
    val plan = planner.convert(
      Action.SaveAction(
        DataSet.natural(Source.BundleLiteral(lit), lit.model), "item1"
      )
    )

    plan.op shouldBe PlanOp.seq(
      PlanOp.PushBundle(lit, PlanFileReference(1)),
      PlanOp.AddMantikItem(
        MantikId("item1"),
        Some(PlanFileReference(1)),
        Mantikfile.pure(
          DataSetDefinition(
            name = None,
            version = None,
            format = "natural",
            `type` = lit.model
          )
        )
      )
    )
  }

  it should "convert a simple fetch operation" in new Env {
    val plan = planner.convert(
      Action.FetchAction(
        DataSet.natural(Source.BundleLiteral(lit), lit.model)
      )
    )
    plan.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true)
    )
    plan.op shouldBe PlanOp.seq(
      PlanOp.PushBundle(lit, PlanFileReference(1)),
      PlanOp.PullBundle(lit.model, PlanFileReference(1))
    )
  }
}

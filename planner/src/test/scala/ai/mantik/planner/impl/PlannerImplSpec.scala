package ai.mantik.planner.impl

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner._
import ai.mantik.repository._
import ai.mantik.testutils.TestBase
import cats.data.State

class PlannerImplSpec extends TestBase {

  private trait Env {
    val isolationSpace = "test"
    val planner = new PlannerImpl(TestItems.testBridges)

    def runWithEmptyState[X](f: => State[PlanningState, X]): (PlanningState, X) = {
      f.run(PlanningState()).value
    }

    val algorithm1 = Algorithm(
      Source(DefinitionSource.Loaded("algo1:version1"),PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)), TestItems.algorithm1
    )
    val dataset1 = DataSet(
      Source(DefinitionSource.Loaded("dataset1:version1"), PayloadSource.Loaded("dataset1", ContentTypes.MantikBundleContentType)), TestItems.dataSet1
    )

    // Create a Source for loaded Items
    def makeLoadedSource(file: String, contentType: String = ContentTypes.MantikBundleContentType, mantikId: MantikId = "item1234"): Source = {
      Source(
        DefinitionSource.Loaded(mantikId),
        PayloadSource.Loaded(file, contentType)
      )
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
        PayloadSource.Empty
      ))
    }
  }

  it should "provide a file when there is a file source" in new Env {
    val (state, source) = runWithEmptyState(planner.translateItemPayloadSource(
      PayloadSource.Loaded("file1", "ContentType")
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
              ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some("ContentType"))
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
      PayloadSource.BundleLiteral(lit)
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
              ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
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
      PayloadSource.OperationResult(
        Operation.Application(
          algorithm1,
          dataset1
        )
      )
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("dataset1")),
      PlanFile(PlanFileReference(2), read = true, fileId = Some("algo1")),
    )
    val expected = ResourcePlan(
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
    source shouldBe expected
  }

  "translateItemPayloadSourceAsFiles" should "convert a file load" in new Env {
    val (state, opFiles) = runWithEmptyState(planner.translateItemPayloadSourceAsFiles(PayloadSource.Loaded("file1", ContentTypes.ZipFileContentType), canBeTemporary = true))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("file1"))
    )
    opFiles.preOp shouldBe PlanOp.Empty
    opFiles.files shouldBe IndexedSeq(PlanFileWithContentType(1, ContentTypes.ZipFileContentType))
  }

  it should "convert a algorithm output" in new Env {
    for {
      temp <- Seq(false, true)
    } {
      val source = PayloadSource.OperationResult(
        Operation.Application(
          algorithm1,
          dataset1
        )
      )
      val (state, opFiles) = runWithEmptyState(planner.translateItemPayloadSourceAsFiles(
        source, canBeTemporary = temp
      ))
      state.files shouldBe List (
        PlanFile(PlanFileReference(1), read = true, fileId = Some("dataset1")),
        PlanFile(PlanFileReference(2), read = true, fileId = Some("algo1")),
        PlanFile(PlanFileReference(3), read = true, write = true, temporary = temp)
      )
      opFiles.fileRefs.head shouldBe PlanFileReference(3)
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
      opFiles.preOp shouldBe expected
    }
  }

  it should "support empty" in new Env {
    val (state, files) = runWithEmptyState(planner.translateItemPayloadSourceAsFiles(
      PayloadSource.Empty, canBeTemporary = true
    ))
    state shouldBe PlanningState()
    files.preOp shouldBe PlanOp.Empty
    files.files shouldBe empty
  }

  "manifestDataSet" should "convert a simple literal source" in new Env {
    val sourcePlan = runWithEmptyState(planner.manifestDataSet(
      DataSet.literal(lit)
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
      makeLoadedSource("file1"), lit.model
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

  it should "manifest a simple loaded item" in new Env {
    val (state, sourcePlan) = runWithEmptyState(planner.manifestDataSet(
      DataSet(makeLoadedSource("file1"), TestItems.dataSet1)
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), read = true, fileId = Some("file1")))
    sourcePlan shouldBe ResourcePlan(
      graph = Graph(
        nodes = Map(
          "1" -> Node(
            PlanNodeService.File(PlanFileReference(1)),
            resources = Map(
              ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
            )
          )
        ),
      ),
      outputs = Seq(NodeResourceRef("1", ExecutorModelDefaults.SourceResource))
    )
  }

  it should "also manifest a bridged dataset" in new Env {
    val (state, sourcePlan) = runWithEmptyState(planner.manifestDataSet(
      DataSet(
        makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.dataSet2)
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), read = true, fileId = Some("file1")))
    sourcePlan shouldBe ResourcePlan(
      graph = Graph(
        nodes = Map(
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("format1_image"), data = Some(PlanFileReference(1)), mantikfile = TestItems.dataSet2),
            resources = Map(
              "get" -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
            )
          )
        ),
      ),
      outputs = Seq(NodeResourceRef("1", "get"))
    )
  }

  it should "manifest the result of an algorithm" in new Env {
    val ds1 = DataSet(makeLoadedSource("1"), Mantikfile.pure(DataSetDefinition(
      format = DataSet.NaturalFormatName, `type` = TabularData("x" -> FundamentalType.Int32)
    )))
    val algo1 = Mantikfile.pure(AlgorithmDefinition(
      stack = "algorithm_stack1",
      `type` = FunctionType(
        input = TabularData("x" -> FundamentalType.Int32),
        output = TabularData("y" -> FundamentalType.Int32)
      )
    ))
    val algorithm = Algorithm(makeLoadedSource("2", ContentTypes.ZipFileContentType), algo1)
    val applied = algorithm.apply(ds1)

    val (state, sourcePlan) = runWithEmptyState(planner.manifestDataSet(
      applied
    ))

    state.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("1")),
      PlanFile(PlanFileReference(2), read = true, fileId = Some("2"))
    )

    val expected = ResourcePlan(
      graph = Graph(
        nodes = Map(
          "1" -> Node(
            PlanNodeService.File(PlanFileReference(1)),
            resources = Map(
              ExecutorModelDefaults.SourceResource -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
            )
          ),
          "2" -> Node(
            PlanNodeService.DockerContainer(Container("algorithm1_image"), data = Some(PlanFileReference(2)), mantikfile = algo1), resources = Map(
              "apply" -> NodeResource(ResourceType.Transformer, Some(ContentTypes.MantikBundleContentType))
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("1", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("2", "apply")
        )
      ),
      outputs = Seq(NodeResourceRef("2", "apply"))
    )

    sourcePlan shouldBe expected
  }

  "manifestTrainableAlgorithm" should "manifest a trainable algorithm" in new Env {
    val (state, sourcePlan) = runWithEmptyState(planner.manifestTrainableAlgorithm(
      TrainableAlgorithm(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.learning1)
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
      Algorithm(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.algorithm1)
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

  "manifestDataSetAsFile" should "return a literal" in new Env {
    val (state, opFiles) = runWithEmptyState(planner.manifestDataSetAsFile(
      DataSet(makeLoadedSource("file1", ContentTypes.MantikBundleContentType), TestItems.dataSet1), canBeTemporary = true
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), read = true, fileId = Some("file1")))
    opFiles.preOp shouldBe PlanOp.Empty
    opFiles.fileRefs shouldBe List(PlanFileReference(1))
  }

  it should "also manifest a bridged dataset" in new Env {
    val (state, opFiles) = runWithEmptyState(planner.manifestDataSetAsFile(
      DataSet(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.dataSet2), canBeTemporary = true
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("file1")),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = true)
    )
    val expected = PlanOp.RunGraph(
      graph = Graph(
        nodes = Map(
          "1" -> Node(
            PlanNodeService.DockerContainer(Container("format1_image"), data = Some(PlanFileReference(1)), mantikfile = TestItems.dataSet2),
            resources = Map(
              "get" -> NodeResource(ResourceType.Source, Some(ContentTypes.MantikBundleContentType))
            )
          ),
          "2" -> Node(
            PlanNodeService.File(PlanFileReference(2)),
            resources = Map(
              ExecutorModelDefaults.SinkResource -> NodeResource(ResourceType.Sink, Some(ContentTypes.MantikBundleContentType))
            )
          )
        ),
        links = Link.links(
          NodeResourceRef("1", "get") -> NodeResourceRef("2", ExecutorModelDefaults.SinkResource)
        )
      )
    )
    opFiles.preOp shouldBe expected
    opFiles.fileRefs shouldBe List(PlanFileReference(2))
  }

  "convert" should "convert a simple save action" in new Env {
    val plan = planner.convert(
      Action.SaveAction(
        DataSet.literal(lit), "item1"
      )
    )

    plan.op shouldBe PlanOp.seq(
      PlanOp.PushBundle(lit, PlanFileReference(1)),
      PlanOp.AddMantikItem(
        MantikId("item1"),
        Some(PlanFileReference(1)),
        Mantikfile.pure(
          DataSetDefinition(
            format = "natural",
            `type` = lit.model
          )
        )
      )
    )
  }

  it should "also convert dependent operations in save actions" in new Env {
    val algorithm2 = Algorithm(
      Source(
        DefinitionSource.Constructed(),
        PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)
      ), TestItems.algorithm1
    )
    val pipeline = Pipeline.build(
      algorithm2
    )
    val plan = planner.convert(
      Action.SaveAction(
        pipeline, "pipe1"
      )
    )
    plan.op shouldBe PlanOp.seq(
      PlanOp.AddMantikItem(
        pipeline.resolved.steps.head.pipelineStep.asInstanceOf[PipelineStep.AlgorithmStep].algorithm,
        Some(PlanFileReference(1)),
        pipeline.resolved.steps.head.algorithm.mantikfile
      ),
      PlanOp.AddMantikItem(
        MantikId("pipe1"),
        None,
        pipeline.mantikfile
      )
    )
  }

  it should "not convert dependent operations, if they are plain loaded" in new Env {
    // the algorithm is already existant (DefinitionSource.Loaded())
    // so there do not have to be a 2nd copy.
    val pipeline = Pipeline.build(
      algorithm1
    )
    val plan = planner.convert(
      Action.SaveAction(
        pipeline, "pipe1"
      )
    )

    plan.op shouldBe PlanOp.AddMantikItem(
      MantikId("pipe1"),
      None,
      pipeline.mantikfile
    )
  }

  it should "convert a simple fetch operation" in new Env {
    val plan = planner.convert(
      Action.FetchAction(
        DataSet.literal(lit)
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

  it should "also work if it has to convert a executed dataset" in new Env {
    val inner = DataSet(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.dataSet2)
    val plan = planner.convert(
      Action.FetchAction (
        inner
      )
    )
    plan.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, fileId = Some("file1")),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = true)
    )
    val (_, opFiles) = runWithEmptyState(planner.manifestDataSetAsFile(inner, true))
    plan.op shouldBe PlanOp.seq(
      opFiles.preOp,
      PlanOp.PullBundle(inner.dataType, PlanFileReference(2))
    )
  }

  it should "convert a simple chained algorithm" in new Env {
    val a = DataSet.literal(lit)
    val b = a.select("select x as y")
    val c = b.select("select y as z")
    val action = c.fetch
    val plan = planner.convert(action)
    plan.op.asInstanceOf[PlanOp.Sequential].plans.size shouldBe 3 // pushing, calculation and pulling
    plan.files.size shouldBe 2 // push file, calculation
  }

  "caching" should "cache simple values in files" in new Env {
    val a = DataSet.literal(lit)
    val b = a.select("select x as y").cached
    val c = b.select("select y as z")
    val plan = planner.convert(c.fetch)
    plan.op.asInstanceOf[PlanOp.Sequential].plans.size shouldBe 3 // cached(pushing, calculation), calculation 2 and pulling
    val cacheKey = b.payloadSource.asInstanceOf[PayloadSource.Cached].cacheGroup.head
    plan.files.size shouldBe 3 // push, cache, calculation
    plan.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = true, cacheKey = Some(cacheKey)),
      PlanFile(PlanFileReference(3), read = true, write = true, temporary = true)
    )
    plan.cacheGroups shouldBe List(List(cacheKey))
    val cachePlan = plan.op.asInstanceOf[PlanOp.Sequential].plans(0).asInstanceOf[PlanOp.CacheOp]
    cachePlan.files shouldBe List(cacheKey -> PlanFileReference(2))
    cachePlan.alternative shouldBe an[PlanOp.Sequential]
    val parts = cachePlan.alternative.asInstanceOf[PlanOp.Sequential].plans
    parts.head shouldBe an[PlanOp.PushBundle]
    parts(1) shouldBe an[PlanOp.RunGraph]


    withClue("It should use the same key for a 2nd invocation referring to the same data") {
      val c2 = b.select("select y as m")
      val plan = planner.convert(c2.fetch)
      val parts = plan.op.asInstanceOf[PlanOp.Sequential].plans
      parts.size shouldBe 3 // calculation of cached, calculation 2 and pulling
      parts.head shouldBe an[PlanOp.CacheOp]
      parts.head.asInstanceOf[PlanOp.CacheOp].cacheGroup shouldBe List(cacheKey)
      parts(1) shouldBe an [PlanOp.RunGraph]
      parts(2) shouldBe an [PlanOp.PullBundle]
    }
  }

  it should "level up temporary files to non temporary ones, if the file is saved at the end" in new Env {
    val a = DataSet.literal(lit)
    val b = a.select("select x as y").cached
    val cacheKey = b.payloadSource.asInstanceOf[PayloadSource.Cached].cacheGroup.head
    val action = b.save("foo1")
    val plan = planner.convert(action)
    plan.files shouldBe List(
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true),
      PlanFile(PlanFileReference(2), read = true, write = true, temporary = false, cacheKey = Some(cacheKey))
    )
    val parts = plan.op.asInstanceOf[PlanOp.Sequential].plans
    parts.size shouldBe 2 // CacheOp, AddMantikItem
    parts.head shouldBe an[PlanOp.CacheOp]
    parts(1) shouldBe an[PlanOp.AddMantikItem]
  }

  it should "automatically cache training outputs" in new Env {
    val trainable = TrainableAlgorithm(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.learning1)
    val trainData = DataSet.literal(Bundle.fundamental(5))
    val (trained, stats) = trainable.train(trainData)

    val trainedPlan = planner.convert(trained.save("algo1"))
    val statsPlan = planner.convert(stats.save("stats1"))
    trainedPlan.cacheGroups shouldNot be(empty)
    statsPlan.cacheGroups shouldNot be(empty)
    trainedPlan.cacheGroups shouldBe statsPlan.cacheGroups
  }
}

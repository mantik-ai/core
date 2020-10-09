package ai.mantik.planner.impl

import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ AlgorithmDefinition, DataSetDefinition, ItemId, MantikHeader, NamedMantikId }
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner.graph.{ Graph, Link, Node, NodePort, NodePortRef }
import ai.mantik.planner.repository.{ Bridge, ContentTypes }
import ai.mantik.planner.{ Algorithm, BuiltInItems, DataSet, DefinitionSource, MantikItemState, Operation, PayloadSource, Pipeline, PlanFile, PlanFileReference, PlanNodeService, PlanOp, Planner, Source, TrainableAlgorithm }
import ai.mantik.testutils.TestBase
import cats.data.State

class ResourcePlanBuilderSpec extends TestBase with PlanTestUtils {

  private trait Env {
    val elements = new PlannerElements(typesafeConfig)
    val mantikItemStateManager = new MantikItemStateManager()
    val resourcePlanBuilder = new ResourcePlanBuilder(elements, mantikItemStateManager)

    def runWithEmptyState[X](f: => State[PlanningState, X]): (PlanningState, X) = {
      f.run(PlanningState()).value
    }

    val algorithm1 = Algorithm(
      Source(DefinitionSource.Loaded(Some("algo1:version1"), ItemId.generate()), PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)),
      TestItems.algorithm1, TestItems.algoBridge
    )
    val dataset1 = DataSet(
      Source(DefinitionSource.Loaded(Some("dataset1:version1"), ItemId.generate()), PayloadSource.Loaded("dataset1", ContentTypes.MantikBundleContentType)),
      TestItems.dataSet1, BuiltInItems.NaturalBridge
    )

    // Create a Source for loaded Items
    def makeLoadedSource(file: String, contentType: String = ContentTypes.MantikBundleContentType, mantikId: NamedMantikId = "item1234"): Source = {
      Source(
        DefinitionSource.Loaded(Some(mantikId), ItemId.generate()),
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
    intercept[Planner.NotAvailableException] {
      runWithEmptyState(resourcePlanBuilder.translateItemPayloadSource(
        PayloadSource.Empty
      ))
    }
  }

  it should "provide a file when there is a file source" in new Env {
    val (state, source) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSource(
      PayloadSource.Loaded("file1", "ContentType")
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), "ContentType", fileId = Some("file1"), read = true)
    )
    source shouldBe ResourcePlan.singleNode(
      "1", Node(
        PlanNodeService.File(PlanFileReference(1)),
        outputs = Vector(
          NodePort("ContentType")
        )
      )
    )
  }

  it should "convert a literal to a file and provide it as stream if given" in new Env {
    val (state, source) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSource(
      PayloadSource.BundleLiteral(lit)
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, write = true, read = true, temporary = true)
    )
    source shouldBe ResourcePlan(
      pre = PlanOp.StoreBundleToFile(lit, PlanFileReference(1)),
      graph = Graph(
        nodes = Map(
          "1" -> Node.source(
            PlanNodeService.File(PlanFileReference(1)),
            ContentTypes.MantikBundleContentType
          )
        )
      ),
      outputs = Seq(
        NodePortRef("1", 0)
      )
    )
  }

  it should "project results of operations" in new Env {
    val (state, source) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSource(
      PayloadSource.OperationResult(
        Operation.Application(
          algorithm1,
          dataset1
        )
      )
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, fileId = Some("dataset1")),
      PlanFile(PlanFileReference(2), ContentTypes.ZipFileContentType, read = true, fileId = Some("algo1"))
    )
    val expected = ResourcePlan(
      graph = Graph(
        nodes = Map(
          "1" -> Node(
            PlanNodeService.File(PlanFileReference(1)),
            outputs = Vector(
              NodePort(ContentTypes.MantikBundleContentType)
            )
          ),
          "2" -> Node(
            PlanNodeService.DockerContainer(Container("docker_algo1"), data = Some(PlanFileReference(2)), mantikHeader = TestItems.algorithm1),
            inputs = Vector(
              NodePort(ContentTypes.MantikBundleContentType)
            ),
            outputs = Vector(
              NodePort(ContentTypes.MantikBundleContentType)
            )
          )
        ),
        links = Link.links(
          NodePortRef("1", 0) -> NodePortRef("2", 0)
        )
      ),
      outputs = Seq(
        NodePortRef("2", 0)
      )
    )
    source shouldBe expected
  }

  "translateItemPayloadSourceAsFiles" should "convert a file load" in new Env {
    val (state, opFiles) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSourceAsFiles(PayloadSource.Loaded("file1", ContentTypes.ZipFileContentType), canBeTemporary = true))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), ContentTypes.ZipFileContentType, read = true, fileId = Some("file1"))
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
      val (state, opFiles) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSourceAsFiles(
        source, canBeTemporary = temp
      ))
      state.files shouldBe List(
        PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, fileId = Some("dataset1")),
        PlanFile(PlanFileReference(2), ContentTypes.ZipFileContentType, read = true, fileId = Some("algo1")),
        PlanFile(PlanFileReference(3), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = temp)
      )
      opFiles.fileRefs.head shouldBe PlanFileReference(3)
      val expected = PlanOp.RunGraph(
        Graph(
          nodes = Map(
            // this part is taken over from stream generation
            "1" -> Node(
              PlanNodeService.File(PlanFileReference(1)),
              outputs = Vector(
                NodePort(ContentTypes.MantikBundleContentType)
              )
            ),
            "2" -> Node(
              PlanNodeService.DockerContainer(Container("docker_algo1"), data = Some(PlanFileReference(2)), mantikHeader = TestItems.algorithm1),
              inputs = Vector(
                NodePort(ContentTypes.MantikBundleContentType)
              ),
              outputs = Vector(
                NodePort(ContentTypes.MantikBundleContentType)
              )
            ),
            // this part is used for generating the file
            "3" -> Node(
              PlanNodeService.File(PlanFileReference(3)),
              inputs = Vector(
                NodePort(ContentTypes.MantikBundleContentType)
              )
            )
          ),
          links = Link.links(
            NodePortRef("1", 0) -> NodePortRef("2", 0),
            NodePortRef("2", 0) -> NodePortRef("3", 0)
          )
        )
      )
      opFiles.preOp shouldBe expected
    }
  }

  it should "support empty" in new Env {
    val (state, files) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSourceAsFiles(
      PayloadSource.Empty, canBeTemporary = true
    ))
    state shouldBe PlanningState()
    files.preOp shouldBe PlanOp.Empty
    files.files shouldBe empty
  }

  it should "support caching" in new Env {
    val cache = PayloadSource.Cached(
      PayloadSource.OperationResult(
        Operation.Application(algorithm1, dataset1)
      ),
      Vector(ItemId.generate())
    )
    val (state, files) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSourceAsFiles(
      cache, canBeTemporary = false
    ))
    state.cacheItems shouldBe Set(cache.siblings)
    splitOps(files.preOp).find(_.isInstanceOf[PlanOp.MarkCached]) shouldBe defined
  }

  it should "detect already cached files" in new Env {
    val cache = PayloadSource.Cached(
      PayloadSource.OperationResult(
        Operation.Application(algorithm1, dataset1)
      ),
      Vector(ItemId.generate())
    )
    mantikItemStateManager.set(cache.siblings.head, MantikItemState(cacheFile = Some("file1")))
    val (state, files) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSourceAsFiles(
      cache, canBeTemporary = false
    ))
    state.cacheItems shouldBe Set(cache.siblings)
    splitOps(files.preOp).find(_.isInstanceOf[PlanOp.MarkCached]) shouldBe empty
  }

  it should "not double-evaluate cached items" in new Env {
    val cache = PayloadSource.Cached(
      PayloadSource.OperationResult(
        Operation.Application(algorithm1, dataset1)
      ),
      Vector(ItemId.generate())
    )
    val (state, files) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSourceAsFiles(
      cache, canBeTemporary = true
    ))
    state.cacheItems shouldBe Set(cache.siblings)
    splitOps(files.preOp).find(_.isInstanceOf[PlanOp.MarkCached]) shouldBe defined

    val (state2, files2) = resourcePlanBuilder.translateItemPayloadSourceAsFiles(
      cache, canBeTemporary = true
    ).run(state).value

    state2 shouldBe state
    files2.files shouldBe files.files
  }

  it should "support projections" in new Env {
    val projection = PayloadSource.Projection(
      PayloadSource.Loaded("file1", ContentTypes.MantikBundleContentType)
    )
    val (state, filesPlan) = runWithEmptyState(resourcePlanBuilder.translateItemPayloadSourceAsFiles(
      projection, canBeTemporary = false
    ))
    state.cacheItems shouldBe empty
    splitOps(filesPlan.preOp) shouldBe empty
    filesPlan.files.size shouldBe 1
  }

  "manifestDataSet" should "convert a simple literal source" in new Env {
    val sourcePlan = runWithEmptyState(resourcePlanBuilder.manifestDataSet(
      DataSet.literal(lit)
    ))._2

    sourcePlan.pre shouldBe PlanOp.StoreBundleToFile(lit, PlanFileReference(1))
    sourcePlan.graph shouldBe Graph(
      Map(
        "1" -> Node(
          PlanNodeService.File(PlanFileReference(1)),
          outputs = Vector(
            NodePort(ContentTypes.MantikBundleContentType)
          )
        )
      )
    )
    sourcePlan.outputs shouldBe Seq(NodePortRef("1", 0))
  }

  it should "convert a load natural source" in new Env {
    val ds = DataSet.natural(
      makeLoadedSource("file1"), lit.model
    )

    val sourcePlan = runWithEmptyState(resourcePlanBuilder.manifestDataSet(
      ds
    ))._2

    sourcePlan.pre shouldBe PlanOp.Empty
    sourcePlan.graph shouldBe Graph(
      Map(
        "1" -> Node(
          PlanNodeService.File(PlanFileReference(1)),
          outputs = Vector(
            NodePort(ContentTypes.MantikBundleContentType)
          )
        )
      )
    )
    sourcePlan.outputs shouldBe Seq(NodePortRef("1", 0))
  }

  it should "manifest a simple loaded item" in new Env {
    val (state, sourcePlan) = runWithEmptyState(resourcePlanBuilder.manifestDataSet(
      DataSet(makeLoadedSource("file1"), TestItems.dataSet1, Bridge.naturalBridge)
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, fileId = Some("file1")))
    sourcePlan shouldBe ResourcePlan.singleNode(
      "1",
      Node.source(
        PlanNodeService.File(PlanFileReference(1)),
        ContentTypes.MantikBundleContentType
      )
    )
  }

  it should "also manifest a bridged dataset" in new Env {
    val (state, sourcePlan) = runWithEmptyState(resourcePlanBuilder.manifestDataSet(
      DataSet(
        makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.dataSet2, TestItems.formatBridge)
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), ContentTypes.ZipFileContentType, read = true, fileId = Some("file1")))
    sourcePlan shouldBe ResourcePlan.singleNode(
      "1",
      Node.source(
        PlanNodeService.DockerContainer(Container("docker_format1"), data = Some(PlanFileReference(1)), mantikHeader = TestItems.dataSet2),
        ContentTypes.MantikBundleContentType
      )
    )
  }

  it should "manifest the result of an algorithm" in new Env {
    val ds1 = DataSet(makeLoadedSource("1"), MantikHeader.pure(DataSetDefinition(
      bridge = Bridge.naturalBridge.mantikId, `type` = TabularData("x" -> FundamentalType.Int32)
    )), Bridge.naturalBridge)
    val algo1 = MantikHeader.pure(AlgorithmDefinition(
      bridge = TestItems.algoBridge.mantikId,
      `type` = FunctionType(
        input = TabularData("x" -> FundamentalType.Int32),
        output = TabularData("y" -> FundamentalType.Int32)
      )
    ))
    val algorithm = Algorithm(makeLoadedSource("2", ContentTypes.ZipFileContentType), algo1, TestItems.algoBridge)
    val applied = algorithm.apply(ds1)

    val (state, sourcePlan) = runWithEmptyState(resourcePlanBuilder.manifestDataSet(
      applied
    ))

    state.files shouldBe List(
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, fileId = Some("1")),
      PlanFile(PlanFileReference(2), ContentTypes.ZipFileContentType, read = true, fileId = Some("2"))
    )

    val expected = ResourcePlan(
      graph = Graph(
        nodes = Map(
          "1" -> Node(
            PlanNodeService.File(PlanFileReference(1)),
            outputs = Vector(
              NodePort(ContentTypes.MantikBundleContentType)
            )
          ),
          "2" -> Node.transformer(
            PlanNodeService.DockerContainer(Container("docker_algo1"), data = Some(PlanFileReference(2)), mantikHeader = algo1),
            ContentTypes.MantikBundleContentType
          )
        ),
        links = Link.links(
          NodePortRef("1", 0) -> NodePortRef("2", 0)
        )
      ),
      outputs = Seq(NodePortRef("2", 0))
    )

    sourcePlan shouldBe expected
  }

  "manifestTrainableAlgorithm" should "manifest a trainable algorithm" in new Env {
    val (state, sourcePlan) = runWithEmptyState(resourcePlanBuilder.manifestTrainableAlgorithm(
      TrainableAlgorithm(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.learning1, TestItems.learningBridge, TestItems.learningBridge)
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), ContentTypes.ZipFileContentType, read = true, fileId = Some("file1")))
    sourcePlan shouldBe ResourcePlan.singleNode(
      "1",
      Node(
        PlanNodeService.DockerContainer(Container("docker_training1"), data = Some(PlanFileReference(1)), mantikHeader = TestItems.learning1),
        inputs = Vector(
          NodePort(
            ContentTypes.MantikBundleContentType
          )
        ),
        outputs = Vector(
          NodePort(ContentTypes.ZipFileContentType),
          NodePort(ContentTypes.MantikBundleContentType)
        )
      )
    )
  }

  "manifestAlgorithm" should "manifest an algorithm" in new Env {
    val (state, sourcePlan) = runWithEmptyState(resourcePlanBuilder.manifestAlgorithm(
      Algorithm(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.algorithm1, TestItems.algoBridge)
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), ContentTypes.ZipFileContentType, read = true, fileId = Some("file1")))
    sourcePlan shouldBe ResourcePlan.singleNode("1", Node.transformer(
      PlanNodeService.DockerContainer(Container("docker_algo1"), data = Some(PlanFileReference(1)), mantikHeader = TestItems.algorithm1),
      ContentTypes.MantikBundleContentType
    ))
  }

  "manifestDataSetAsFile" should "return a literal" in new Env {
    val (state, opFiles) = runWithEmptyState(resourcePlanBuilder.manifestDataSetAsFile(
      DataSet(makeLoadedSource("file1", ContentTypes.MantikBundleContentType), TestItems.dataSet1, Bridge.naturalBridge), canBeTemporary = true
    ))

    state.files shouldBe List(PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, fileId = Some("file1")))
    opFiles.preOp shouldBe PlanOp.Empty
    opFiles.fileRefs shouldBe List(PlanFileReference(1))
  }

  it should "also manifest a bridged dataset" in new Env {
    val (state, opFiles) = runWithEmptyState(resourcePlanBuilder.manifestDataSetAsFile(
      DataSet(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.dataSet2, TestItems.formatBridge), canBeTemporary = true
    ))
    state.files shouldBe List(
      PlanFile(PlanFileReference(1), ContentTypes.ZipFileContentType, read = true, fileId = Some("file1")),
      PlanFile(PlanFileReference(2), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true)
    )
    val expected = PlanOp.RunGraph(
      graph = Graph(
        nodes = Map(
          "1" -> Node.source(
            PlanNodeService.DockerContainer(Container("docker_format1"), data = Some(PlanFileReference(1)), mantikHeader = TestItems.dataSet2),
            ContentTypes.MantikBundleContentType
          ),
          "2" -> Node.sink(
            PlanNodeService.File(PlanFileReference(2)),
            ContentTypes.MantikBundleContentType
          )
        ),
        links = Link.links(
          NodePortRef("1", 0) -> NodePortRef("2", 0)
        )
      )
    )
    opFiles.preOp shouldBe expected
    opFiles.fileRefs shouldBe List(PlanFileReference(2))
  }

  "manifestPipeline" should "manifest a pipeline" in new Env {
    val algorithm2 = Algorithm(
      Source(
        DefinitionSource.Constructed(),
        PayloadSource.Loaded("algo2", ContentTypes.ZipFileContentType)
      ), TestItems.algorithm2, TestItems.algoBridge
    )
    val pipeline = Pipeline.build(
      algorithm1,
      algorithm2
    )

    val (state, pipe) = runWithEmptyState(resourcePlanBuilder.manifestPipeline(pipeline))
    pipe.inputs.size shouldBe 1
    pipe.outputs.size shouldBe 1
    pipe.graph.nodes.size shouldBe 2
  }
}

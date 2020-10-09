package ai.mantik.planner.impl

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.elements.{ ItemId, NamedMantikId }
import ai.mantik.planner._
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.testutils.TestBase
import cats.data.State

class PlannerImplSpec extends TestBase with PlanTestUtils {

  private trait Env {
    val stateManager = new MantikItemStateManager()
    val planner = new PlannerImpl(typesafeConfig, stateManager)

    def state(item: MantikItem): MantikItemState = stateManager.getOrInit(item)

    def runWithEmptyState[X](f: => State[PlanningState, X]): (PlanningState, X) = {
      f.run(PlanningState()).value
    }

    val algorithm1 = Algorithm(
      Source(DefinitionSource.Loaded(Some("algo1:version1"), ItemId.generate()), PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)), TestItems.algorithm1,
      TestItems.algoBridge
    )
    val dataset1 = DataSet(
      Source(DefinitionSource.Loaded(Some("dataset1:version1"), ItemId.generate()), PayloadSource.Loaded("dataset1", ContentTypes.MantikBundleContentType)), TestItems.dataSet1,
      TestItems.formatBridge
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

  "storeSingleItem" should "store an item" in new Env {
    val ds = DataSet.literal(lit)
    val (state, result) = runWithEmptyState(planner.storeSingleItem(ds))
    splitOps(result.preOp).find(_.isInstanceOf[PlanOp.AddMantikItem]).get shouldBe PlanOp.AddMantikItem(
      ds, Some(PlanFileReference(1))
    )
    result.files.head.ref shouldBe PlanFileReference(1)
    state.files.head.ref shouldBe PlanFileReference(1)
    state.overrideState(ds, stateManager).payloadAvailable.get.head.ref shouldBe PlanFileReference(1)
    state(ds).itemStored shouldBe false // items are NOT updated by Planner
  }

  "ensureItemStored" should "do nothing if the item is alrady stored" in new Env {
    // dataset is already stored
    val (state, result) = runWithEmptyState(planner.ensureItemStored(dataset1))
    result.preOp shouldBe PlanOp.Empty
    result.fileRefs.head shouldBe PlanFileReference(1)
  }

  it should "store the item if not yet stored" in new Env {
    val ds = DataSet.literal(lit)
    val (state, result) = runWithEmptyState(planner.ensureItemStored(ds))
    splitOps(result.preOp).find(_.isInstanceOf[PlanOp.AddMantikItem]) shouldBe defined
    result.fileRefs.head shouldBe PlanFileReference(1)

    withClue("It should not lead to a change if the item is stored twice") {
      val (state2, result2) = planner.ensureItemStored(ds).run(state).value
      result2.preOp shouldBe PlanOp.Empty
      result2.fileRefs.head shouldBe PlanFileReference(1)
      state2 shouldBe state
    }
  }

  "ensureAlgorithmDeployed" should "ensure an algorithm is deployed" in new Env {
    val (state, result) = runWithEmptyState(planner.ensureAlgorithmDeployed(algorithm1))
    state(algorithm1).deployment shouldBe empty // no state change
    state.overrideState(algorithm1, stateManager).deployed shouldBe Some(Right("var1"))
    val ops = splitOps(result)
    ops.size shouldBe 2
    ops(0) shouldBe an[PlanOp.DeployAlgorithm]
    ops(1) shouldBe an[PlanOp.MemoryWriter[_]]

    val (state2, result2) = planner.ensureAlgorithmDeployed(algorithm1).run(state).value
    state2 shouldBe state
    result2 shouldBe an[PlanOp.MemoryReader[_]]
  }

  "deployPipeline" should "do nothing, if already deployed" in new Env {
    val pipe = Pipeline.build(algorithm1)
    val deploymentState = DeploymentState(name = "foo1", internalUrl = "http://url1")
    stateManager.upsert(pipe, _.copy(deployment = Some(deploymentState)))
    val (state, result) = runWithEmptyState(planner.deployPipeline(pipe, Some("name1"), Some("ingress1")))
    state shouldBe PlanningState()
    result shouldBe PlanOp.Const(deploymentState)
  }

  it should "deploy a pipeline" in new Env {
    val pipe = Pipeline.build(algorithm1)
    val (state, result) = runWithEmptyState(planner.deployPipeline(pipe, Some("name1"), Some("ingress1")))
    state(pipe).deployment shouldBe empty // no state changes
    state.overrideState(pipe, stateManager).deployed shouldBe Some(Right("var2"))
    withClue("dependent items are also deployed") {
      state.overrideState(algorithm1, stateManager).deployed shouldBe Some(Right("var1"))
    }
    withClue("It must also be stored") {
      state.overrideState(pipe, stateManager).stored shouldBe true
    }
  }

  it should "also store all dependent items, if not yet done" in new Env {
    val unstored = Algorithm(
      Source(
        DefinitionSource.Constructed(),
        PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)
      ), TestItems.algorithm1, TestItems.algoBridge
    )
    val pipe = Pipeline.build(unstored)
    val (state, result) = runWithEmptyState(planner.deployPipeline(pipe, Some("name1"), Some("ingress1")))

    state.overrideState(unstored, stateManager).stored shouldBe true
    state.overrideState(pipe, stateManager).stored shouldBe true
  }

  "convert" should "convert a simple save action" in new Env {
    val item = DataSet.literal(lit)
    val plan = planner.convert(
      Action.SaveAction(
        item
      )
    )

    plan.op shouldBe PlanOp.seq(
      PlanOp.StoreBundleToFile(lit, PlanFileReference(1)),
      PlanOp.AddMantikItem(
        item, Some(PlanFileReference(1))
      )
    )
  }

  it should "work with a tag and save" in new Env {
    val item = DataSet.literal(lit).tag("hello1")
    val plan = planner.convert(
      Action.SaveAction(
        item
      )
    )

    plan.op shouldBe PlanOp.seq(
      PlanOp.StoreBundleToFile(lit, PlanFileReference(1)),
      PlanOp.AddMantikItem(
        item, Some(PlanFileReference(1))
      )
    )
  }

  it should "also convert dependent operations in save actions" in new Env {
    val algorithm2 = Algorithm(
      Source(
        DefinitionSource.Constructed(),
        PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)
      ), TestItems.algorithm1, TestItems.algoBridge
    )
    val pipeline = Pipeline.build(
      algorithm2
    )
    val plan = planner.convert(
      Action.SaveAction(
        pipeline
      )
    )
    val algorithmName = pipeline.resolved.steps.head
    plan.op shouldBe PlanOp.seq(
      PlanOp.AddMantikItem(
        algorithm2,
        Some(PlanFileReference(1))
      ),
      PlanOp.AddMantikItem(
        pipeline,
        None
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
        pipeline
      )
    )
    splitOps(plan.op) shouldBe Seq(
      PlanOp.AddMantikItem(
        pipeline,
        None
      ))
  }

  it should "tag an item if it was loaded before and is just going to be resaved" in new Env {
    val tagged = algorithm1.tag("newname")
    val plan = planner.convert(
      Action.SaveAction(
        tagged
      )
    )
    splitOps(plan.op) shouldBe Seq(
      PlanOp.TagMantikItem(
        tagged, "newname"
      )
    )
  }

  it should "convert a simple fetch operation" in new Env {
    val plan = planner.convert(
      Action.FetchAction(
        DataSet.literal(lit)
      )
    )
    plan.files shouldBe List(
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true)
    )
    plan.op shouldBe PlanOp.seq(
      PlanOp.StoreBundleToFile(lit, PlanFileReference(1)),
      PlanOp.LoadBundleFromFile(lit.model, PlanFileReference(1))
    )
  }

  it should "also work if it has to convert a executed dataset" in new Env {
    val inner = DataSet(makeLoadedSource("file1", ContentTypes.ZipFileContentType), TestItems.dataSet2, TestItems.formatBridge)
    val plan = planner.convert(
      Action.FetchAction(
        inner
      )
    )
    plan.files shouldBe List(
      PlanFile(PlanFileReference(1), ContentTypes.ZipFileContentType, read = true, fileId = Some("file1")),
      PlanFile(PlanFileReference(2), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true)
    )
    val (_, opFiles) = runWithEmptyState(planner.resourcePlanBuilder.manifestDataSetAsFile(inner, true))
    plan.op shouldBe PlanOp.seq(
      opFiles.preOp,
      PlanOp.LoadBundleFromFile(inner.dataType, PlanFileReference(2))
    )
  }

  it should "convert a simple chained algorithm" in new Env {
    val a = DataSet.literal(lit)
    val b = a.select("select x as y")
    val c = b.select("select y as z")
    val action = c.fetch
    val plan = planner.convert(action)
    val ops = splitOps(plan.op)
    ops.size shouldBe 3 // pushing, calculation and pulling
    plan.files.size shouldBe 2 // push file, calculation
  }

  it should "convert a deploy action algorithm action" in new Env {
    val deployAction = algorithm1.deploy() // algorithm1 is loaded, so it doesn't have to be stored.
    val plan = planner.convert(deployAction)
    val ops = splitOps(plan.op)
    ops.size shouldBe 2
    ops.head shouldBe an[PlanOp.DeployAlgorithm]
    ops(1) shouldBe an[PlanOp.MemoryWriter[_]]
  }

  it should "save a non-loaded algorithm first" in new Env {
    val algorithm2 = Algorithm(
      Source(DefinitionSource.Constructed(), PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)), TestItems.algorithm1, TestItems.algoBridge
    )
    val deployAction = algorithm2.deploy()
    val plan = planner.convert(deployAction)
    val ops = splitOps(plan.op)
    ops.find(_.isInstanceOf[PlanOp.AddMantikItem]) shouldBe defined
    ops.find(_.isInstanceOf[PlanOp.DeployAlgorithm]) shouldBe defined
  }

  it should "convert a push operation" in new Env {
    val a = DataSet.literal(lit)
    val pushAction = a.tag("foo1").push()
    val plan = planner.convert(pushAction)
    val ops = splitOps(plan.op)
    ops.size shouldBe 3
    ops(0) shouldBe an[PlanOp.StoreBundleToFile]
    ops(1) shouldBe an[PlanOp.AddMantikItem]
    ops(2) shouldBe an[PlanOp.PushMantikItem]
  }

  "caching" should "cache simple values in files" in new Env {
    val a = DataSet.literal(lit)
    val b = a.select("select x as y").cached
    val c = b.select("select y as z")
    val plan = planner.convert(c.fetch)
    val ops = splitOps(plan.op)
    ops.size shouldBe 5 // pushing, calculation, mark cached, calculation2, pulling
    val expectedItemId = b.payloadSource.asInstanceOf[PayloadSource.Cached].siblings.head
    b.itemId shouldBe expectedItemId

    plan.files.size shouldBe 3 // push, cache, calculation
    plan.files shouldBe List(
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true),
      PlanFile(PlanFileReference(2), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true, cacheItemId = Some(expectedItemId)),
      PlanFile(PlanFileReference(3), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true)
    )
    plan.cachedItems shouldBe Set(Vector(expectedItemId))

    ops(0) shouldBe an[PlanOp.StoreBundleToFile]
    ops(1) shouldBe an[PlanOp.RunGraph]
    ops(2) shouldBe an[PlanOp.MarkCached]

    val cachePlan = ops(2).asInstanceOf[PlanOp.MarkCached]
    cachePlan.files shouldBe List(expectedItemId -> PlanFileReference(2))

    withClue("It should use the same key for a 2nd invocation referring to the same data") {
      val c2 = b.select("select y as m")
      val plan = planner.convert(c2.fetch)
      val parts = splitOps(plan.op)
      parts.size shouldBe 5 // calculation of cached, calculation 2 and pulling
      parts(2) shouldBe an[PlanOp.MarkCached]
      parts(2).asInstanceOf[PlanOp.MarkCached].siblingIds shouldBe Vector(expectedItemId)
      parts(3) shouldBe an[PlanOp.RunGraph]
      parts(4) shouldBe an[PlanOp.LoadBundleFromFile]
    }
  }

  it should "level up temporary files to non temporary ones, if the file is saved at the end" in new Env {
    val a = DataSet.literal(lit)
    val b = a.select("select x as y").cached
    val expectedItemId = b.payloadSource.asInstanceOf[PayloadSource.Cached].siblings.head
    b.itemId shouldBe expectedItemId
    val action = b.tag("foo1").save()
    val plan = planner.convert(action)
    plan.files shouldBe List(
      // literal
      PlanFile(PlanFileReference(1), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true),
      // cache
      PlanFile(PlanFileReference(2), ContentTypes.MantikBundleContentType, read = true, write = true, temporary = true, cacheItemId = Some(expectedItemId)),
      // copied cache for saving
      PlanFile(PlanFileReference(3), ContentTypes.MantikBundleContentType, read = true, write = true) // also readable, because it can be used in later invocations
    )
    val parts = splitOps(plan.op)
    parts.size shouldBe 5
    parts(2) shouldBe an[PlanOp.MarkCached]
    parts(3) shouldBe an[PlanOp.CopyFile] // copy file to non-cached
    parts(4) shouldBe an[PlanOp.AddMantikItem]
    parts(4).asInstanceOf[PlanOp.AddMantikItem].file shouldBe Some(PlanFileReference(3))
  }

  it should "automatically cache training outputs" in new Env {
    val trainable = TrainableAlgorithm(
      MantikItemCore(
        makeLoadedSource("file1", ContentTypes.ZipFileContentType),
        TestItems.learning1,
        TestItems.learningBridge
      ),
      TestItems.learningBridge
    )
    val trainData = DataSet.literal(Bundle.fundamental(5))
    val (trained, stats) = trainable.train(trainData)

    val trainedPlan = planner.convert(trained.save())
    val statsPlan = planner.convert(stats.save())
    trainedPlan.cachedItems shouldNot be(empty)
    statsPlan.cachedItems shouldNot be(empty)
    trainedPlan.cachedItems shouldBe statsPlan.cachedItems
  }
}

package ai.mantik.planner.impl

import ai.mantik.ds.element.Bundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.elements
import ai.mantik.elements.{AlgorithmDefinition, DataSetDefinition, ItemId, MantikId, Mantikfile, PipelineStep}
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.planner._
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.testutils.TestBase
import cats.data.State

class PlannerImplSpec extends TestBase {

  private trait Env {
    val planner = new PlannerImpl(TestItems.testBridges)

    def runWithEmptyState[X](f: => State[PlanningState, X]): (PlanningState, X) = {
      f.run(PlanningState()).value
    }

    val algorithm1 = Algorithm(
      Source(DefinitionSource.Loaded("algo1:version1", ItemId.generate()),PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)), TestItems.algorithm1
    )
    val dataset1 = DataSet(
      Source(DefinitionSource.Loaded("dataset1:version1", ItemId.generate()), PayloadSource.Loaded("dataset1", ContentTypes.MantikBundleContentType)), TestItems.dataSet1
    )

    // Create a Source for loaded Items
    def makeLoadedSource(file: String, contentType: String = ContentTypes.MantikBundleContentType, mantikId: MantikId = "item1234"): Source = {
      Source(
        DefinitionSource.Loaded(mantikId, ItemId.generate()),
        PayloadSource.Loaded(file, contentType)
      )
    }

    def splitOps(op: PlanOp): Seq[PlanOp] = {
      op match {
        case PlanOp.Sequential(parts) => parts
        case PlanOp.Empty => Nil
        case other => Seq(other)
      }
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
    state.itemStorage(ds)._2.get.payload.get.ref shouldBe PlanFileReference(1)
    ds.state.get.isStored shouldBe false // items are NOT updated by Planner
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

    withClue("It should not lead to a change if the item is stored twice"){
      val (state2, result2) = planner.ensureItemStored(ds).run(state).value
      result2.preOp shouldBe PlanOp.Empty
      result2.fileRefs.head shouldBe PlanFileReference(1)
      state2 shouldBe state
    }
  }

  "ensureAlgorithmDeployed" should "ensure an algorithm is deployed" in new Env {
    val (state, result) = runWithEmptyState(planner.ensureAlgorithmDeployed(algorithm1))
    algorithm1.state.get.deployment shouldBe empty // no state change
    state.itemDeployed(algorithm1) shouldBe defined
    val ops = splitOps(result)
    // no storage, as algorithm1 is already stored
    ops.find(_.isInstanceOf[PlanOp.DeployAlgorithm]) shouldBe defined

    val (state2, result2) = planner.ensureAlgorithmDeployed(algorithm1).run(state).value
    state2 shouldBe state
    result2 shouldBe PlanOp.Empty
  }

  "deployPipeline" should "do nothing, if already deployed" in new Env {
    val pipe = Pipeline.build(algorithm1)
    val deploymentState = DeploymentState(name = "foo1", internalUrl = "http://url1")
    pipe.state.update(x => x.copy(deployment = Some(deploymentState)))
    val (state, result) = runWithEmptyState(planner.deployPipeline(pipe, Some("name1"), Some("ingress1")))
    state shouldBe PlanningState()
    result shouldBe PlanOp.Const(deploymentState)
  }

  it should "deploy a pipeline" in new Env {
    val pipe = Pipeline.build(algorithm1)
    val (state, result) = runWithEmptyState(planner.deployPipeline(pipe, Some("name1"), Some("ingress1")))
    pipe.state.get.deployment shouldBe empty // no state changes
    state.itemDeployed(pipe) shouldBe defined
    withClue("dependent items are also deployed"){
      state.itemDeployed(algorithm1) shouldBe defined
    }
    withClue("It must also be stored"){
      state.itemStorage(pipe)._2 shouldBe defined
    }
  }

  it should "also store all dependent items, if not yet done" in new Env {
    val unstored = Algorithm(
      Source(
        DefinitionSource.Constructed(),
        PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)
      ), TestItems.algorithm1
    )
    val pipe = Pipeline.build(unstored)
    val (state, result) = runWithEmptyState(planner.deployPipeline(pipe, Some("name1"), Some("ingress1")))

    state.itemStorage(unstored)._2 shouldBe defined
    state.itemStorage(pipe)._2 shouldBe defined
  }

  "convert" should "convert a simple save action" in new Env {
    val item = DataSet.literal(lit)
    val plan = planner.convert(
      Action.SaveAction(
        item, "item1"
      )
    )

    plan.op shouldBe PlanOp.seq(
      PlanOp.StoreBundleToFile(lit, PlanFileReference(1)),
      PlanOp.AddMantikItem(
        item, Some(PlanFileReference(1)),
      ),
      PlanOp.TagMantikItem(
        item, "item1"
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
    val algorithmName = pipeline.resolved.steps.head.pipelineStep.asInstanceOf[PipelineStep.AlgorithmStep].algorithm
    plan.op shouldBe PlanOp.seq(
      PlanOp.AddMantikItem(
        algorithm2,
        Some(PlanFileReference(1)),
      ),
      PlanOp.AddMantikItem(
        pipeline,
        None,
      ),
      PlanOp.TagMantikItem(
        pipeline,
        "pipe1"
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
    splitOps(plan.op) shouldBe Seq(
      PlanOp.AddMantikItem(
        pipeline,
        None
      ),
      PlanOp.TagMantikItem(
        pipeline,
        "pipe1"
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
      PlanFile(PlanFileReference(1), read = true, write = true, temporary = true)
    )
    plan.op shouldBe PlanOp.seq(
      PlanOp.StoreBundleToFile(lit, PlanFileReference(1)),
      PlanOp.LoadBundleFromFile(lit.model, PlanFileReference(1))
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
    plan.op.asInstanceOf[PlanOp.Sequential].plans.size shouldBe 3 // pushing, calculation and pulling
    plan.files.size shouldBe 2 // push file, calculation
  }

  it should "convert a deploy action algorithm action" in new Env {
    val deployAction = algorithm1.deploy() // algorithm1 is loaded, so it doesn't have to be stored.
    val plan = planner.convert(deployAction)
    plan.op shouldBe an[PlanOp.DeployAlgorithm]
  }

  it should "save a non-loaded algorithm first" in new Env {
    val algorithm2 = Algorithm(
      Source(DefinitionSource.Constructed(),PayloadSource.Loaded("algo1", ContentTypes.ZipFileContentType)), TestItems.algorithm1
    )
    val deployAction = algorithm2.deploy()
    val plan = planner.convert(deployAction)
    val ops = plan.op.asInstanceOf[PlanOp.Sequential].plans
    ops.find(_.isInstanceOf[PlanOp.AddMantikItem]) shouldBe defined
    ops.find(_.isInstanceOf[PlanOp.DeployAlgorithm]) shouldBe defined
  }

  it should "convert a push operation" in new Env {
    val a = DataSet.literal(lit)
    val pushAction = a.push("foo1")
    val plan = planner.convert(pushAction)
    val ops = splitOps(plan.op)
    ops.size shouldBe 4
    ops(0) shouldBe an[PlanOp.StoreBundleToFile]
    ops(1) shouldBe an[PlanOp.AddMantikItem]
    ops(2) shouldBe an[PlanOp.TagMantikItem]
    ops(3) shouldBe an[PlanOp.PushMantikItem]
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
    parts.head shouldBe an[PlanOp.StoreBundleToFile]
    parts(1) shouldBe an[PlanOp.RunGraph]


    withClue("It should use the same key for a 2nd invocation referring to the same data") {
      val c2 = b.select("select y as m")
      val plan = planner.convert(c2.fetch)
      val parts = plan.op.asInstanceOf[PlanOp.Sequential].plans
      parts.size shouldBe 3 // calculation of cached, calculation 2 and pulling
      parts.head shouldBe an[PlanOp.CacheOp]
      parts.head.asInstanceOf[PlanOp.CacheOp].cacheGroup shouldBe List(cacheKey)
      parts(1) shouldBe an [PlanOp.RunGraph]
      parts(2) shouldBe an [PlanOp.LoadBundleFromFile]
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
    val parts = splitOps(plan.op)
    parts.size shouldBe  3 // CacheOp, AddMantikItem, TagMantikItem
    parts.head shouldBe an[PlanOp.CacheOp]
    parts(1) shouldBe an[PlanOp.AddMantikItem]
    parts(2) shouldBe an[PlanOp.TagMantikItem]
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

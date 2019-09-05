package ai.mantik.engine.server.services

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.element.Bundle
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ AlgorithmDefinition, ItemId, Mantikfile, NamedMantikId }
import ai.mantik.engine.protos.ds.BundleEncoding
import ai.mantik.engine.protos.graph_executor.{ DeployItemRequest, FetchItemRequest, SaveItemRequest }
import ai.mantik.engine.testutil.TestBaseWithSessions
import ai.mantik.planner.repository.MantikArtifact
import ai.mantik.planner.{ Algorithm, DataSet, DeploymentState, MantikItem, Pipeline, PlanOp, Source }

import scala.concurrent.Future

class GraphExecutorServiceImplSpec extends TestBaseWithSessions {

  trait Env {
    val graphExecutor = new GraphExecutorServiceImpl(sessionManager)
  }

  for {
    encoding <- Seq(BundleEncoding.ENCODING_MSG_PACK, BundleEncoding.ENCODING_JSON)
  } {
    it should s"fetch datasets with ${encoding}" in new Env {
      val lit = Bundle.fundamental("Hello World")
      val session1 = await(sessionManager.create())
      val dataset = DataSet.literal(lit)
      val dataset1Id = session1.addItem(
        dataset
      )
      components.nextItemToReturnByExecutor = Future.successful(lit)
      val response = await(graphExecutor.fetchDataSet(
        FetchItemRequest(session1.id, dataset1Id, encoding)
      ))
      val bundleDecoded = await(Converters.decodeBundle(response.bundle.get))
      bundleDecoded shouldBe lit
      components.lastPlan shouldBe components.planner.convert(dataset.fetch)
    }
  }

  "save" should "save elements" in new Env {
    val lit = Bundle.fundamental("Hello World")
    val session1 = await(sessionManager.create())
    val dataset = DataSet.literal(lit)
    val dataset1Id = session1.addItem(
      dataset
    )
    components.nextItemToReturnByExecutor = Future.successful(())
    val response = await(graphExecutor.saveItem(SaveItemRequest(
      session1.id, dataset1Id, "foo1"
    )))
    response.name shouldBe "foo1"
    response.mantikItemId shouldNot be(empty)
    components.lastPlan shouldBe components.planner.convert(dataset.tag("foo1").save())
  }

  "deploy" should "deploy elements" in new Env {
    val algorithm1Artifact = MantikArtifact(
      Mantikfile.pure(AlgorithmDefinition(stack = "tf.saved_model", `type` = FunctionType(FundamentalType.Int32, FundamentalType.StringType))),
      fileId = Some("1236"),
      namedId = Some(NamedMantikId("Algorithm1")),
      itemId = ItemId.generate()
    )
    val algorithm1 = MantikItem.fromMantikArtifact(algorithm1Artifact).asInstanceOf[Algorithm]
    val pipeline1 = Pipeline.build(
      algorithm1
    )
    val session1 = await(sessionManager.create())
    val pipeline1Id = session1.addItem(
      pipeline1
    )
    components.nextItemToReturnByExecutor = Future.successful(
      DeploymentState(
        name = "name1",
        internalUrl = "internalUrl1",
        externalUrl = Some("externalUrl1")
      )
    )
    val response = await(graphExecutor.deployItem(
      DeployItemRequest(
        session1.id,
        pipeline1Id,
        nameHint = "nameHint1",
        ingressName = "ingress1"
      )
    ))
    response.name shouldBe "name1"
    response.internalUrl shouldBe "internalUrl1"
    response.externalUrl shouldBe "externalUrl1"
    val ops = components.lastPlan.op.asInstanceOf[PlanOp.Sequential[_]].plans
    val deployOp = ops.collectFirst {
      case x: PlanOp.DeployPipeline => x
    }.get
    deployOp.serviceNameHint shouldBe Some("nameHint1")
    deployOp.ingress shouldBe Some("ingress1")
  }
}

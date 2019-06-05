package ai.mantik.engine.server.services

import ai.mantik.ds.element.Bundle
import ai.mantik.engine.protos.ds.BundleEncoding
import ai.mantik.engine.protos.graph_executor.{ FetchItemRequest, SaveItemRequest }
import ai.mantik.engine.testutil.TestBaseWithSessions
import ai.mantik.planner.DataSet

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
    components.lastPlan shouldBe components.planner.convert(dataset.save("foo1"))
  }
}

package ai.mantik.planner.integration

import java.net.InetSocketAddress
import java.nio.file.{Path, Paths}

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.NamedMantikId
import ai.mantik.elements.errors.{ErrorCodes, MantikException}
import ai.mantik.planner.impl.{RemotePlanningContextImpl, RemotePlanningContextServerImpl}
import ai.mantik.planner.protos.planning_context.PlanningContextServiceGrpc.{PlanningContextService, PlanningContextServiceStub}
import ai.mantik.planner.{DataSet, MantikItem, PlanningContext}
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.grpc.netty.NettyServerBuilder

abstract class PlanningContextSpecBase extends IntegrationTestBase  {

  protected def planningContext: PlanningContext

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    context.pushLocalMantikItem(Paths.get("bridge/binary"))
  }

  "load" should "load an mantik item" in {
    intercept[MantikException]{
      planningContext.loadDataSet("loadTest1")
    }.code shouldBe ErrorCodes.MantikItemNotFound

    planningContext.execute(DataSet.literal(
      Bundle.fundamental(10)
    ).tag("loadTest1").save())

    val got = planningContext.loadDataSet("loadTest1")
    got.mantikId shouldBe NamedMantikId("loadTest1")

    val got2 = planningContext.load("loadTest1")
    got2 shouldBe got

    def checkBadType(f: (PlanningContext, String) => MantikItem): Unit = {
      intercept[MantikException]{
        f(planningContext, "loadTest1")
      }.code shouldBe ErrorCodes.MantikItemWrongType
    }
    checkBadType(_.loadPipeline(_))
    checkBadType(_.loadAlgorithm(_))
    checkBadType(_.loadTrainableAlgorithm(_))
  }

  "pull" should "pull an item from a remote repository" in {
    // can't test this easily without a working remote repo
    pending
  }

  "execute" should "execute an operation" in {
    val simpleInput = DataSet.literal(
      Bundle.buildColumnWise.withPrimitives(
        "x", 1, 2, 3
      ).result
    )
    val increased = simpleInput.select("select x + 1 as y")
    val got = planningContext.execute(increased.fetch)
    got shouldBe Bundle.buildColumnWise.withPrimitives(
      "y", 2, 3, 4
    ).result
  }

  "pushLocalItem" should "add a local item and use it's default name" in {
    intercept[MantikException]{
      planningContext.loadDataSet("mnist_dataset")
    }.code shouldBe ErrorCodes.MantikItemNotFound

    val name = planningContext.pushLocalMantikItem(Paths.get("bridge/binary/test/mnist"))
    name shouldBe NamedMantikId("mnist_test")

    withClue("No exception expected"){
      planningContext.loadDataSet("mnist_test")
    }
  }

  it should "use the supplied name if given" in {
    intercept[MantikException]{
      planningContext.loadDataSet("foo")
    }.code shouldBe ErrorCodes.MantikItemNotFound

    val name = planningContext.pushLocalMantikItem(Paths.get("bridge/binary/test/mnist"), Some("foo"))
    name shouldBe NamedMantikId("foo")

    withClue("No exception expected"){
      planningContext.loadDataSet("foo")
    }
  }
}

class PlanningContextSpec extends PlanningContextSpecBase {
  override protected def planningContext: PlanningContext = context
}

class RemotePlanningContextSpec extends PlanningContextSpecBase {
  private lazy val service = new RemotePlanningContextServerImpl(
    context, retriever
  )

  private lazy val grpcServer = {
    val server = NettyServerBuilder
      .forAddress(new InetSocketAddress("localhost", 0))
      .addService(PlanningContextService.bindService(service, ec))
      .build()

    server.start()
    server
  }

  private lazy val channel: ManagedChannel = ManagedChannelBuilder.forAddress("localhost", grpcServer.getPort).usePlaintext().build()

  override lazy val planningContext: PlanningContext = {
    val remoteContextStub = new PlanningContextServiceStub(channel)
    val remoteContext = new RemotePlanningContextImpl(remoteContextStub)
    remoteContext
  }

  override protected def afterAll(): Unit = {
    channel.shutdown()
    grpcServer.shutdownNow()
    super.afterAll()
  }
}

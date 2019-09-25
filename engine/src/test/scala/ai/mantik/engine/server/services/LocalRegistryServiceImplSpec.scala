package ai.mantik.engine.server.services

import java.time.Instant

import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.ds.FundamentalType.{ Int32, StringType }
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ AlgorithmDefinition, DataSetDefinition, ItemId, MantikDefinition, Mantikfile, NamedMantikId }
import ai.mantik.engine.protos.local_registry.{ GetArtifactRequest, ListArtifactsRequest }
import ai.mantik.engine.testutil.TestBaseWithSessions
import ai.mantik.planner.repository.{ DeploymentInfo, MantikArtifact }
import com.google.protobuf.timestamp.Timestamp
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException

class LocalRegistryServiceImplSpec extends TestBaseWithSessions {

  trait Env {
    val localRegistryServiceImpl = new LocalRegistryServiceImpl(components.localRegistry)
    val localRepo = components.repository

    val item = new MantikArtifact(
      Mantikfile.pure(
        AlgorithmDefinition(stack = "some_stack", `type` = FunctionType(Int32, Int32))
      ),
      fileId = Some("1234"),
      namedId = Some("item1"),
      itemId = ItemId.generate(),
      deploymentInfo = None
    )

    val item2 = item.copy(
      mantikfile = Mantikfile.pure(
        DataSetDefinition(format = "format", `type` = StringType)
      ),
      itemId = ItemId.generate(),
      namedId = None
    )

    val item3 = item.copy(
      deploymentInfo = Some(
        DeploymentInfo(
          name = "abcd",
          internalUrl = "internal1",
          externalUrl = Some("external1"),
          timestamp = Instant.parse("2019-09-19T11:18:24.123Z") // Note: database has only milliseconds precision
        )
      ),
      itemId = ItemId.generate(),
      namedId = Some("item3")
    )

    await(localRepo.store(item))
    await(localRepo.store(item2))
    await(localRepo.store(item3))

  }

  "get" should "should fail for not existing" in new Env {
    val e = intercept[StatusRuntimeException] {
      await(localRegistryServiceImpl.getArtifact(
        GetArtifactRequest("notexisting")
      ))
    }
    e.getStatus.getCode shouldBe Code.NOT_FOUND
  }

  it should "return a regular element" in new Env {
    val back = Converters.decodeMantikArtifact(await(localRegistryServiceImpl.getArtifact(
      GetArtifactRequest(item.namedId.get.toString)
    )).artifact.get)
    back shouldBe item

    withClue("It should work with item ids") {
      val got = await(localRegistryServiceImpl.getArtifact(GetArtifactRequest(item.itemId.toString)))
      Converters.decodeMantikArtifact(got.artifact.get) shouldBe back.copy(
        namedId = None
      )
    }
  }

  it should "return a deployed element" in new Env {
    val back = await(localRegistryServiceImpl.getArtifact(
      GetArtifactRequest("item3")
    )).artifact.get.deploymentInfo.get

    back.name shouldBe "abcd"
    back.internalUrl shouldBe "internal1"
    back.externalUrl shouldBe "external1"
    RpcConversions.decodeInstant(Timestamp.toJavaProto(back.timestamp.get)) shouldBe
      item3.deploymentInfo.get.timestamp
  }

  it should "list items" in new Env {
    await(localRegistryServiceImpl.listArtifacts(
      ListArtifactsRequest()
    )).artifacts.map(Converters.decodeMantikArtifact) should contain theSameElementsAs Seq(
      item,
      item3
    )
    await(localRegistryServiceImpl.listArtifacts(
      ListArtifactsRequest(anonymous = true)
    )).artifacts.map(Converters.decodeMantikArtifact) should contain theSameElementsAs Seq(
      item,
      item2,
      item3
    )
    await(localRegistryServiceImpl.listArtifacts(
      ListArtifactsRequest(kind = MantikDefinition.DataSetKind, anonymous = true)
    )).artifacts.map(Converters.decodeMantikArtifact) should contain theSameElementsAs Seq(
      item2
    )
  }
}

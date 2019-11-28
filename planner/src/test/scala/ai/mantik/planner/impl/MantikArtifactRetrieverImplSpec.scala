package ai.mantik.planner.impl

import java.nio.file.Paths

import ai.mantik.ds.FundamentalType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.{ AlgorithmDefinition, BridgeDefinition, DataSetDefinition, MantikDefinition, NamedMantikId, PipelineDefinition, PipelineStep }
import ai.mantik.planner.BuiltInItems
import ai.mantik.planner.repository.{ ContentTypes, FileRepositoryServer, MantikArtifact, RemoteMantikRegistry }
import ai.mantik.planner.repository.impl.{ LocalFileRepository, LocalMantikRegistryImpl, LocalRepository, MantikArtifactRetrieverImpl }
import ai.mantik.planner.util.{ ErrorCodeTestUtils, TestBaseWithAkkaRuntime }
import ai.mantik.testutils.{ AkkaSupport, TempDirSupport, TestBase }

class MantikArtifactRetrieverImplSpec extends TestBaseWithAkkaRuntime with TempDirSupport with ErrorCodeTestUtils {

  trait Env {
    val remoteRegistry = RemoteMantikRegistry.empty

    val fileRepo = new LocalFileRepository(tempDirectory)
    val repo = new LocalRepository(tempDirectory)

    val localRegistry = new LocalMantikRegistryImpl(fileRepo, repo)

    val retriever = new MantikArtifactRetrieverImpl(
      localRegistry,
      remoteRegistry
    )
  }

  private val binaryBridge = Paths.get("bridge/binary")
  private val sampleDir = Paths.get("bridge/binary/test/mnist")

  "addLocalDirectoryToRepository" should "work" in new Env {
    await(retriever.addLocalDirectoryToRepository(binaryBridge))
    val id = NamedMantikId("mnist_test")
    val got = await(retriever.addLocalDirectoryToRepository(sampleDir))
    got.namedId shouldBe Some(id)
    got.fileId shouldBe defined
    await(localRegistry.get(id)) shouldBe got
    val (contentType, payloadSource) = await(localRegistry.getPayload(got.fileId.get))
    contentType shouldBe ContentTypes.ZipFileContentType
    withClue("Consuming the file should work") {
      collectByteSource(payloadSource)
    }
  }

  it should "fail on missing dependencies" in new Env {
    // Bridge missing
    interceptErrorCode(ErrorCodes.MantikItemNotFound) {
      await(retriever.addLocalDirectoryToRepository(sampleDir))
    }
  }

  it should "accept another mantik id" in new Env {
    await(retriever.addLocalDirectoryToRepository(binaryBridge))
    val otherId = NamedMantikId("boom")
    val got = await(retriever.addLocalDirectoryToRepository(sampleDir, Some(otherId)))
    got.namedId shouldBe Some(otherId)
    await(localRegistry.get(otherId)) shouldBe got
  }

  "pull" should "work" in {
    pending
  }

  "get" should "work" in {
    pending
  }

  it should "skip built in items" in new Env {
    val artifact = MantikArtifact.makeFromDefinition(
      DataSetDefinition(
        bridge = BuiltInItems.NaturalBridgeName,
        `type` = FundamentalType.Int32
      ),
      name = "foo1"
    )
    await(localRegistry.addMantikArtifact(artifact, None))
    val (again, hull) = await(retriever.get("foo1"))
    hull shouldBe empty
    again shouldBe artifact
  }

  it should "read transitive dependencies" in new Env {
    val bridge = MantikArtifact.makeFromDefinition(BridgeDefinition(
      dockerImage = "foo1",
      suitable = Seq(MantikDefinition.AlgorithmKind)
    ), "bridge1")
    val algorithm1 = MantikArtifact.makeFromDefinition(AlgorithmDefinition(
      bridge = bridge.namedId.get,
      `type` = FunctionType(
        FundamentalType.Int32, FundamentalType.Int32
      )
    ), "algorithm1")
    val pipeline = MantikArtifact.makeFromDefinition(PipelineDefinition(
      steps = List(
        PipelineStep.AlgorithmStep(algorithm1.namedId.get)
      )
    ), "pipeline1")
    await(localRegistry.addMantikArtifact(bridge, None))
    await(localRegistry.addMantikArtifact(algorithm1, None))
    await(localRegistry.addMantikArtifact(pipeline, None))

    val (back, hull) = await(retriever.get(pipeline.namedId.get))
    back shouldBe pipeline
    hull shouldBe Seq(algorithm1, bridge)
  }
}

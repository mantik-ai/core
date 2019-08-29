package ai.mantik.planner.impl

import java.nio.file.Paths

import ai.mantik.elements.MantikId
import ai.mantik.planner.repository.{ ContentTypes, FileRepositoryServer, RemoteMantikRegistry }
import ai.mantik.planner.repository.impl.{ LocalFileRepository, LocalMantikRegistryImpl, LocalRepository, MantikArtifactRetrieverImpl }
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.{ AkkaSupport, TempDirSupport, TestBase }

class MantikArtifactRetrieverImplSpec extends TestBaseWithAkkaRuntime with TempDirSupport {

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

  private val sampleDir = Paths.get("bridge/binary/test/mnist")

  "addLocalDirectoryToRepository" should "work" in new Env {
    val id = MantikId("mnist_test")
    val got = await(retriever.addLocalDirectoryToRepository(sampleDir))
    got.id shouldBe id
    await(localRegistry.get(id)) shouldBe got
    val (contentType, payloadSource) = await(localRegistry.getPayload(got.fileId.get))
    contentType shouldBe ContentTypes.ZipFileContentType
    withClue("Consuming the file should work") {
      collectByteSource(payloadSource)
    }
  }

  it should "accept another mantik id" in new Env {
    val otherId = MantikId("boom")
    val got = await(retriever.addLocalDirectoryToRepository(sampleDir, Some(otherId)))
    got.id shouldBe otherId
    await(localRegistry.get(otherId)) shouldBe got
  }

  "pull" should "work" in {
    pending
  }

  "get" should "work" in {
    pending
  }
}

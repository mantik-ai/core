package ai.mantik.repository.impl

import java.nio.file.Files

import ai.mantik.repository.{ ContentTypes, Errors }
import ai.mantik.testutils.{ FakeClock, TestBase }
import com.typesafe.config.{ Config, ConfigValue, ConfigValueFactory }
import org.apache.commons.io.FileUtils

import scala.concurrent.duration._

class LocalFileRepositorySpec extends FileRepositorySpecBase {

  val fakeClock = new FakeClock()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    fakeClock.resetTime()
  }

  override protected def config: Config = {
    val tempDir = Files.createTempDirectory("mantik_local_storage_test")
    super.config.withValue(
      "mantik.repository.fileRepository.local.directory", ConfigValueFactory.fromAnyRef(tempDir.toString)
    )
  }

  override type FileRepoType = LocalFileRepository with NonAsyncFileRepository

  override protected def createRepo: FileRepoType = new LocalFileRepository(config, fakeClock) with NonAsyncFileRepository {
    override def shutdown(): Unit = {
      super.shutdown()
      FileUtils.deleteDirectory(directory.toFile)
    }
  }

  it should "parse timeout config values" in {
    withRepo { repo =>
      repo.cleanupInterval shouldBe 1.hours
      repo.cleanupTimeout shouldBe 48.hours
      repo.timeoutScheduler.isCancelled shouldBe false
    }
  }

  it should "disable the scheduler on shutdown" in {
    val repo = new LocalFileRepository(config, fakeClock)
    repo.timeoutScheduler.isCancelled shouldBe false
    repo.shutdown()
    repo.timeoutScheduler.isCancelled shouldBe true
    FileUtils.deleteDirectory(repo.directory.toFile)
  }

  "listFiles" should "work" in {
    withRepo { repo =>
      val req1 = repo.requestFileStorageSync(true)
      val req2 = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
      val req3 = repo.requestAndStoreSync(false, ContentTypes.MantikBundleContentType, testBytes)
      repo.listFiles().toIndexedSeq should contain theSameElementsAs Seq(req1.fileId, req2.fileId, req3.fileId)
    }
  }

  "automatic cleanup" should "automatically clean temporary files" in {
    withRepo { repo =>
      val storeResult = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
      fakeClock.setTimeOffset(repo.cleanupTimeout.minus(1.seconds))
      repo.removeTimeoutedFiles()
      repo.getFileContentSync(storeResult.fileId) shouldBe testBytes

      fakeClock.setTimeOffset(repo.cleanupTimeout.plus(1.seconds))
      repo.removeTimeoutedFiles()
      intercept[Errors.NotFoundException] {
        repo.getFileContentSync(storeResult.fileId)
      }
    }
  }

  it should "automatically clean up files without content" in {
    withRepo { repo =>
      val storeResult = await(repo.requestFileStorage(true))
      fakeClock.setTimeOffset(repo.cleanupTimeout.minus(1.seconds))
      repo.removeTimeoutedFiles()

      repo.listFiles().toSet should contain(storeResult.fileId)

      fakeClock.setTimeOffset(repo.cleanupTimeout.plus(1.seconds))
      repo.removeTimeoutedFiles()
      repo.listFiles().toSet should not(contain(storeResult.fileId))

      intercept[Errors.NotFoundException] {
        await(repo.storeFile(storeResult.fileId, "somecontenttype"))
      }
    }
  }

  it should "not remove non-temporary files" in {
    withRepo { repo =>
      val storeResult = repo.requestAndStoreSync(false, ContentTypes.MantikBundleContentType, testBytes)
      fakeClock.setTimeOffset(repo.cleanupTimeout.plus(1.hour))
      repo.removeTimeoutedFiles()
      repo.getFileContentSync(storeResult.fileId) shouldBe testBytes
      repo.listFiles().toSet should contain(storeResult.fileId)
    }
  }
}

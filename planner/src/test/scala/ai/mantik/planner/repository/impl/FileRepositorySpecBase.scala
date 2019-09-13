package ai.mantik.planner.repository.impl

import ai.mantik.componently.AkkaRuntime
import ai.mantik.planner.repository.{ ContentTypes, Errors, FileRepository }
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.{ TempDirSupport }
import akka.util.ByteString

import scala.util.Random

abstract class FileRepositorySpecBase extends TestBaseWithAkkaRuntime with TempDirSupport {

  type RepoType <: FileRepository with NonAsyncFileRepository

  protected def createRepo(): RepoType

  trait Env {
    val repo = createRepo()
  }

  protected val testBytes = ByteString {
    val bytes = new Array[Byte](1000)
    Random.nextBytes(bytes)
    bytes
  }

  it should "save and load a file" in new Env {
    val info = await(repo.requestFileStorage(false))
    repo.storeFileSync(info.fileId, ContentTypes.MantikBundleContentType, testBytes)
    val get = repo.getFileSync(info.fileId, false)
    get.isTemporary shouldBe false
    val (contentType, bytesAgain) = repo.getFileContentSync(info.fileId)
    contentType shouldBe ContentTypes.MantikBundleContentType
    bytesAgain shouldBe testBytes
  }

  it should "know optimistic storage" in new Env {
    val info = await(repo.requestFileStorage(true))

    intercept[Errors.NotFoundException] {
      repo.getFileSync(info.fileId, optimistic = false)
    }
    val getFileResponse = withClue("No exception expected here") {
      repo.getFileSync(info.fileId, optimistic = true)
    }
    getFileResponse.isTemporary shouldBe true
    // now store some content
    repo.storeFileSync(info.fileId, ContentTypes.MantikBundleContentType, testBytes)

    repo.getFileContentSync(info.fileId) shouldBe (ContentTypes.MantikBundleContentType -> testBytes)
  }

  it should "allow file removal " in new Env {
    val req = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
    val result = await(repo.deleteFile(req.fileId))
    result shouldBe true
    intercept[Errors.NotFoundException] {
      repo.getFileContentSync(req.fileId)
    }
    val nonExistingResult = await(repo.deleteFile("unknown"))
    nonExistingResult shouldBe false
  }
}

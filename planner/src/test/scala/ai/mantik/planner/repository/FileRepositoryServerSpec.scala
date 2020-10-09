package ai.mantik.planner.repository

import ai.mantik.elements.errors.MantikException
import ai.mantik.planner.repository.impl.NonAsyncFileRepository
import ai.mantik.planner.repository.impl.LocalFileRepository
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.{ TempDirSupport, TestBase }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentType, HttpEntity, HttpMethods, HttpRequest, MediaType, Uri }
import akka.http.scaladsl.model.headers.Accept
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }

import scala.util.Random

class FileRepositoryServerSpec extends TestBaseWithAkkaRuntime with TempDirSupport {

  override protected lazy val typesafeConfig: Config = ConfigFactory.load().withValue(
    "mantik.repository.fileRepository.port", ConfigValueFactory.fromAnyRef(0)
  )

  protected val MantikBundleContentType = ContentType.apply(
    MediaType.custom(ContentTypes.MantikBundleContentType, true).asInstanceOf[MediaType.Binary]
  )

  protected def filePrefix = "/files/"

  trait Env {
    val repo = new LocalFileRepository(tempDirectory) with NonAsyncFileRepository
    val server = new FileRepositoryServer(repo)

    val rootUri: Uri = {
      val address = server.address()
      Uri(s"http://localhost:${address.port}")
    }

    val fileUri = Uri("files/").resolvedAgainst(rootUri)
  }

  protected val testBytes = ByteString {
    val bytes = new Array[Byte](1000)
    Random.nextBytes(bytes)
    bytes
  }

  // Custom Content Type

  it should "return 200 on root paths" in new Env {
    val response = await(Http().singleRequest(HttpRequest(uri = fileUri)))
    response.status.intValue() shouldBe 200
    val response2 = await(Http().singleRequest(HttpRequest(uri = s"http://localhost:${server.address().port}")))
    response2.status.intValue() shouldBe 200
  }

  it should "allow file upload and download" in new Env {
    val s = await(repo.requestFileStorage(ContentTypes.MantikBundleContentType, true))
    s.path shouldBe s"files/${s.fileId}"

    val uri = Uri(s.fileId).resolvedAgainst(fileUri)

    val postRequest = HttpRequest(method = HttpMethods.POST, uri = uri)
      .withEntity(HttpEntity(MantikBundleContentType, testBytes))

    val postResponse = await(Http().singleRequest(
      postRequest
    ))
    postResponse.status.isSuccess() shouldBe true

    val getRequest = HttpRequest(uri = uri).addHeader(
      Accept(MantikBundleContentType.mediaType)
    )
    val getResponse = await(Http().singleRequest(getRequest))
    getResponse.status.intValue() shouldBe 200
    val bytes = collectByteSource(getResponse.entity.dataBytes)
    bytes shouldBe testBytes
  }

  it should "know it's address" in new Env {
    val address = server.address()
    address.host shouldNot startWith("127.0.") // No loopback devices
  }

  it should "allow file removal " in new Env {
    val req = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
    val result = await(repo.deleteFile(req.fileId))
    result shouldBe true
    intercept[MantikException] {
      repo.getFileContentSync(req.fileId)
    }.code.isA(FileRepository.NotFoundCode) shouldBe true
    val nonExistingResult = await(repo.deleteFile("unknown"))
    nonExistingResult shouldBe false
  }
}

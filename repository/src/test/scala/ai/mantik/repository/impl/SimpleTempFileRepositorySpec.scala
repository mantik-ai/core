package ai.mantik.repository.impl

import ai.mantik.ds.testutil.TestBase
import ai.mantik.testutils.AkkaSupport
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{ConfigFactory, ConfigValue, ConfigValueFactory}

import scala.util.Random

class SimpleTempFileRepositorySpec extends TestBase with AkkaSupport {

  private var repo: SimpleTempFileRepository = _
  private val MantikBundleContentTypeString = "application/x-mantik-bundle"
  private val testBytes = new Array[Byte](1000)
  Random.nextBytes(testBytes)

  // Custom Content Type
  val MantikBundleContentType = ContentType.apply(
    MediaType.custom(MantikBundleContentTypeString, true).asInstanceOf[MediaType.Binary]
  )

  val config = ConfigFactory.load().withValue(
    "mantik.repository.fileRepository.port", ConfigValueFactory.fromAnyRef(0)
  )

  override def beforeEach: Unit = {
    repo = new SimpleTempFileRepository(config)
  }

  override def afterEach(): Unit = {
    super.afterEach()
    repo.shutdown()
  }

  private def rootUri: Uri =  Uri(s"http://localhost:${repo.boundPort}/files/")

  it should "return 200 on root paths" in {
    val response = await(Http().singleRequest(HttpRequest(uri = rootUri)))
    response.status.intValue() shouldBe 200
    val response2 = await(Http().singleRequest(HttpRequest(uri = s"http://localhost:${repo.boundPort}")))
    response2.status.intValue() shouldBe 200
  }

  it should "allow file upload and download" in {
    val s = await(repo.requestFileStorage(true))
    s.executorClusterUrl shouldBe repo.externalUrl
    s.resource shouldBe s.fileId

    val uri = Uri(s.fileId).resolvedAgainst(rootUri)

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
    bytes shouldBe ByteString(testBytes)
  }

  it should "allow direct storage" in {
    val s = await(repo.requestFileStorage(true))
    val sink = await(repo.storeFile(s.fileId, MantikBundleContentTypeString))
    await(Source.single(ByteString(testBytes)).runWith(sink))

    val getResult = await(repo.requestFileGet(s.fileId))
    getResult.fileId shouldBe s.fileId
    getResult.contentType shouldBe Some(MantikBundleContentTypeString)

    val source = await(repo.loadFile(s.fileId))
    val bytes = collectByteSource(source)
    bytes shouldBe ByteString(testBytes)
  }

  // TODO More tests
}

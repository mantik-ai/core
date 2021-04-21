package ai.mantik.executor.s3storage

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.Errors
import ai.mantik.testutils.{AkkaSupport, HttpSupport, TestBase}
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.duration._
import scala.util.Random

class S3StorageSpec extends TestBase with AkkaSupport with HttpSupport {
  implicit def akkaRuntime: AkkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)

  trait Env {
    val s3Config = S3Config
      .fromTypesafe(typesafeConfig)
      .withTestTag()
    val s3Storage = new S3Storage(s3Config)
  }

  it should "upload, download and dete the file" in new Env {
    val randomName = UUID.randomUUID().toString
    val fileContent = ByteString("Hello World")
    val sink = await(s3Storage.storeFile(randomName, fileContent.length))
    val result = await(Source.single(fileContent).toMat(sink)(Keep.right).run())
    result.bytes shouldBe fileContent.size

    val tagResult = await(s3Storage.getFileTags(randomName))
    logger.info(s"Tags = ${tagResult}")
    tagResult.tags.get("managed-by") shouldBe Some("mantik")

    val source = await(s3Storage.getFile(randomName))
    val bytestring = collectByteSource(source)
    bytestring shouldBe fileContent

    await(s3Storage.deleteFile(randomName)).found.forall(_ == true) shouldBe true

    val resultAgain = s3Storage.getFile(randomName)
    awaitException[Errors.NotFoundException] {
      resultAgain
    }

    withClue("Delete should not fail if the file is not existing") {
      await(s3Storage.deleteFile(randomName)).found.forall(_ == false) shouldBe true
    }
  }

  trait EnvWithFile extends Env {
    val anotherName = UUID.randomUUID().toString
    val fileContent = for {
      chunk <- 0 until 100
    } yield {
      val bytes = new Array[Byte](1024)
      Random.nextBytes(bytes)
      ByteString(bytes)
    }
    val contentLength = fileContent.map(_.length).sum
    val sink = await(s3Storage.storeFile(anotherName, contentLength))
    await(Source(fileContent).runWith(sink))
  }

  it should "be possible to share a file" in new EnvWithFile {
    val shareResult = await(s3Storage.shareFile(anotherName, 1.hours))

    shareResult.url shouldNot be(empty)
    val currentTime = Instant.now()
    shareResult.expiration.isAfter(currentTime.plus(50, ChronoUnit.MINUTES)) shouldBe true
    shareResult.expiration.isBefore(currentTime.plus(70, ChronoUnit.MINUTES)) shouldBe true

    val (_, data) = httpGet(shareResult.url)
    data shouldBe fileContent.reduceLeft(_ ++ _)

    s3Storage.deleteFile(anotherName)
  }

  it should "be possible to make file public" in new EnvWithFile {
    val url = s3Storage.getUrl(anotherName)
    val (response, _) = httpGet(url)
    response.status.intValue() shouldBe 403

    val aclResult = await(s3Storage.setAcl(anotherName, true))

    withClue("When ACL workaround is activated, the public copy should also have tags.") {
      try {
        val tagResult = await(s3Storage.getFileTags(s"public/${anotherName}"))
        tagResult.tags.get("managed-by") shouldBe Some("mantik")
      } catch {
        case _: Errors.NotFoundException => // ok
      }

    }

    // Aclresult.url may be different
    val (response2, bytes) = httpGet(aclResult.url)
    response2.status.intValue() shouldBe 200
    bytes shouldBe fileContent.reduceLeft(_ ++ _)

    await(s3Storage.setAcl(anotherName, false))
    httpGet(url)._1.status.intValue() shouldBe 403
    httpGet(aclResult.url)._1.status.intValue() shouldBe 404
  }

  it should "not be public if deleted" in new EnvWithFile {
    val url = s3Storage.getUrl(anotherName)
    val (response, _) = httpGet(url)
    response.status.intValue() shouldBe 403

    val aclResult = await(s3Storage.setAcl(anotherName, true))
    val (response2, _) = httpGet(aclResult.url)
    response2.status.intValue() shouldBe 200

    await(s3Storage.deleteFile(anotherName))

    val (response3, _) = httpGet(aclResult.url)
    response3.status.intValue() shouldBe 404
  }

  "deleteAllManaged" should "work" in new EnvWithFile {
    await(s3Storage.listManaged()).elements shouldNot be(empty)
    await(s3Storage.deleteAllManaged())
    await(s3Storage.listManaged()).elements shouldBe empty

    awaitException[Errors.NotFoundException](s3Storage.getFile(anotherName))
  }
}

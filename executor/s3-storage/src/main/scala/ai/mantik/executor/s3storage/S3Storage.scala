package ai.mantik.executor.s3storage

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.executor.{Errors, ExecutorFileStorage}
import ai.mantik.componently.utils.JavaFutureConverter._
import ai.mantik.executor.ExecutorFileStorage.{DeleteResult, StoreFileResult}
import ai.mantik.executor.s3storage.S3Storage.{ListResponse, ListResponseElement}
import akka.NotUsed
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl._
import akka.util.ByteString
import com.google.common.util.concurrent.SettableFuture
import org.reactivestreams.{Publisher, Subscriber}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer, SdkPublisher}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Client}
import software.amazon.awssdk.services.s3.model.{
  CopyObjectRequest,
  DeleteObjectRequest,
  DeleteObjectResponse,
  GetObjectRequest,
  GetObjectResponse,
  GetObjectTaggingRequest,
  GetUrlRequest,
  ListObjectsRequest,
  ListObjectsV2Request,
  NoSuchKeyException,
  ObjectCannedACL,
  PutObjectAclRequest,
  PutObjectRequest,
  PutObjectTaggingRequest,
  S3Exception,
  Tag,
  Tagging
}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

import java.lang
import java.net.URI
import java.nio.ByteBuffer
import java.util.Optional
import java.util.concurrent.{CompletableFuture, CompletionException}
import java.util.concurrent.atomic.AtomicLong
import javax.inject.{Inject, Singleton}
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._

@Singleton
class S3Storage(s3Config: S3Config)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with ExecutorFileStorage {

  @Inject
  def this()(implicit akkaRuntime: AkkaRuntime) = {
    this(S3Config.fromTypesafe(akkaRuntime.config))
  }

  private val cred = AwsBasicCredentials.create(
    s3Config.accessId,
    s3Config.secretId.read()
  )

  val UseAclWorkaround = true

  private val credentialsProvider = StaticCredentialsProvider.create(cred)

  private val endpoint = new URI(s3Config.endpoint)
  private val s3Client = S3AsyncClient
    .builder()
    .credentialsProvider(credentialsProvider)
    .endpointOverride(endpoint)
    .region(Region.of(s3Config.region))
    .build()

  private val s3Presigner = S3Presigner
    .builder()
    .credentialsProvider(credentialsProvider)
    .endpointOverride(endpoint)
    .region(Region.of(s3Config.region))
    .build()

  addShutdownHook {
    Future {
      s3Client.close()
    }
  }

  /** Returns default tags. */
  def defaultTags: Map[String, String] = s3Config.tags

  override def storeFile(id: String, contentLength: Long): Future[Sink[ByteString, Future[StoreFileResult]]] =
    handleErrors {
      logger.debug(s"Preparing Storage of ${id}")
      val tagging = Tagging
        .builder()
        .tagSet(
          s3Config.tags.toSeq.map { case (key, value) =>
            Tag.builder().key(key).value(value).build()
          }: _*
        )
        .build()

      val req = PutObjectRequest
        .builder()
        .bucket(s3Config.bucket)
        .contentLength(contentLength)
        .key(id)
        .tagging(tagging)
        .build()

      Future {
        val (publisher, sink) = bytePublisherSink()

        val response = s3Client
          .putObject(
            req,
            AsyncRequestBody.fromPublisher(publisher)
          )
          .asScala

        val counter = new AtomicLong()
        val wrappedSink = sink
          .contramap[ByteString] { bytes =>
            counter.addAndGet(bytes.length)
            bytes
          }
          .mapMaterializedValue { _ =>
            response.map { s3Response =>
              val bytes = counter.get()
              logger.debug(s"Stored ${id} with ${bytes} bytes")
              StoreFileResult(
                bytes = bytes
              )
            }
          }

        wrappedSink
      }
    }

  /** Generates a connected pair of publisher and sink for byte handling */
  private def bytePublisherSink(): (Publisher[ByteBuffer], Sink[ByteString, NotUsed]) = {
    val (publisher, sink) = Sink
      .asPublisher[ByteBuffer](false)
      .preMaterialize()

    val mappedSink = sink.contramap[ByteString] { data =>
      data.toByteBuffer
    }
    (publisher, mappedSink)
  }

  override def getFile(id: String): Future[Source[ByteString, NotUsed]] = handleErrors {
    val req = GetObjectRequest
      .builder()
      .bucket(s3Config.bucket)
      .key(id)
      .build()

    val result = Promise[Source[ByteString, NotUsed]]

    s3Client.getObject(
      req,
      new AsyncResponseTransformer[GetObjectResponse, GetObjectResponse] {

        @volatile
        var resultFuture: CompletableFuture[GetObjectResponse] = _

        override def prepare(): CompletableFuture[GetObjectResponse] = {
          resultFuture = new CompletableFuture[GetObjectResponse]()
          resultFuture
        }

        override def onResponse(response: GetObjectResponse): Unit = {
          resultFuture.complete(response)
        }

        override def onStream(publisher: SdkPublisher[ByteBuffer]): Unit = {
          val source = Source.fromPublisher(publisher).map { byteBuffer =>
            ByteString.fromByteBuffer(byteBuffer)
          }
          result.trySuccess(source)
        }

        override def exceptionOccurred(error: Throwable): Unit = {
          resultFuture.completeExceptionally(error)
          result.tryFailure(error)
        }
      }
    )

    result.future
  }

  /** Get file tags. */
  def getFileTags(id: String): Future[S3Storage.GetFileTagsResult] = handleErrors {
    val req = GetObjectTaggingRequest
      .builder()
      .bucket(s3Config.bucket)
      .key(id)
      .build()

    s3Client.getObjectTagging(req).asScala.map { response =>
      val tags = response
        .tagSet()
        .asScala
        .map { tag =>
          tag.key() -> tag.value()
        }
        .toMap
      S3Storage.GetFileTagsResult(tags)
    }
  }

  /** Returns true if the object is managed by this mantik instance */
  def isManaged(id: String): Future[Boolean] = {
    getFileTags(id).map { tagResult =>
      s3Config.tags.forall { case (key, value) =>
        tagResult.tags.get(key).contains(value)
      }
    }
  }

  override def deleteFile(id: String): Future[DeleteResult] = {
    val mainResponse = deleteFileRaw(id).map { response =>
      logger.debug(s"Deleted ${id}, response: ${response}")
      DeleteResult(
        // S3 doesn't tell if the file was found
        found = None
      )
    }

    if (UseAclWorkaround) {
      mainResponse.andThen { case _ =>
        // Also delete potential public copy
        setAclWorkaround(id, false)
      }
    } else {
      mainResponse
    }
  }

  private def deleteFileRaw(id: String): Future[DeleteObjectResponse] = handleErrors {
    val req = DeleteObjectRequest
      .builder()
      .bucket(s3Config.bucket)
      .key(id)
      .build()

    s3Client.deleteObject(req).asScala
  }

  override def shareFile(id: String, duration: FiniteDuration): Future[ExecutorFileStorage.ShareResult] = handleErrors {
    Future {
      val objectGet = GetObjectRequest
        .builder()
        .bucket(s3Config.bucket)
        .key(id)
        .build()

      val request = GetObjectPresignRequest
        .builder()
        .getObjectRequest(objectGet)
        .signatureDuration(java.time.Duration.ofSeconds(duration.toSeconds))
        .build()

      val presignedObjectGetRequest = s3Presigner.presignGetObject(request)

      presignedObjectGetRequest.expiration()

      ExecutorFileStorage.ShareResult(
        url = presignedObjectGetRequest.url().toString,
        expiration = presignedObjectGetRequest.expiration()
      )
    }
  }

  override def setAcl(id: String, public: Boolean): Future[ExecutorFileStorage.SetAclResult] = {
    if (UseAclWorkaround) {
      return setAclWorkaround(id, public)
    }
    handleErrors {
      val acl = if (public) {
        ObjectCannedACL.PUBLIC_READ
      } else {
        ObjectCannedACL.PRIVATE
      }
      val putAclRequest = PutObjectAclRequest
        .builder()
        .bucket(s3Config.bucket)
        .key(id)
        .acl(
          acl
        )
        .build()

      s3Client.putObjectAcl(putAclRequest).asScala.map { response =>
        ExecutorFileStorage.SetAclResult(getUrl(id))
      }
    }
  }

  private def setAclWorkaround(id: String, public: Boolean): Future[ExecutorFileStorage.SetAclResult] = handleErrors {
    /*
    Minio doesn't support ACLs and no Conditional Policies for tagging https://github.com/minio/minio/issues/10265
    however we can copy it to another name and make this name public.
     */
    val publicId = s"public/${id}"

    if (public) {
      val request = CopyObjectRequest
        .builder()
        .copySource(s"${s3Config.bucket}/${id}")
        .destinationBucket(s3Config.bucket)
        .destinationKey(publicId)
        .build()

      s3Client
        .copyObject(
          request
        )
        .asScala
        .map { _ =>
          ExecutorFileStorage.SetAclResult(getUrl(publicId))
        }
    } else {
      val request = DeleteObjectRequest
        .builder()
        .bucket(s3Config.bucket)
        .key(publicId)
        .build()
      s3Client
        .deleteObject(
          request
        )
        .asScala
        .map { _ =>
          ExecutorFileStorage.SetAclResult(getUrl(publicId))
        }
    }

  }

  override def getUrl(id: String): String = {
    s3Client
      .utilities()
      .getUrl(
        GetUrlRequest
          .builder()
          .endpoint(new URI(s3Config.endpoint))
          .bucket(s3Config.bucket)
          .key(id)
          .build()
      )
      .toString
  }

  def handleErrors[T](in: Future[T]): Future[T] = {
    in.recoverWith {
      case x if errorHandler.isDefinedAt(x) => Future.failed(errorHandler(x))
    }
  }

  val errorHandler: PartialFunction[Throwable, Throwable] = {
    case e: CompletionException if errorHandler.isDefinedAt(e.getCause) => errorHandler(e.getCause)
    case e: NoSuchKeyException =>
      new Errors.NotFoundException(e.getMessage, e)

  }

  /**
    * Delete all Managed files.
    * Note: this call is mainly for integration tests.
    * Note: this is slow.
    * @return number of deleted files
    */
  def deleteAllManaged(): Future[Int] = handleErrors {
    // Safety net, so that we don't delete something else
    if (!s3Config.hasTestTeg) {
      throw new IllegalStateException(s"Only supported for tests")
    }
    logger.info(s"Deleting managed S3 Content...")

    listManaged().flatMap { managed =>
      val deleteFutures = managed.elements.map { element =>
        deleteFileRaw(element.id)
      }
      Future.sequence(deleteFutures).map { _ =>
        logger.info(s"Finished deleting ${managed.elements.size} managed elements")
        managed.elements.size
      }
    }
  }

  /**
    * List all managed objects
    * Note: this call is mainly for integration tests
    * Node: this is slow.
    */
  def listManaged(): Future[ListResponse] = handleErrors {
    // According to docs there is no way to filter on tags directly.
    // So we filter by hand, which is ok for the load of integration tests.
    listAllObjects().flatMap { allObjects =>
      val subFutures = allObjects.elements.map { singleObject =>
        isManaged(singleObject.id).map { managed =>
          singleObject -> managed
        }
      }
      Future
        .sequence(subFutures)
        .map { results =>
          results.collect { case (singleObject, true) =>
            singleObject
          }
        }
        .map { ListResponse }
    }
  }

  /** List all objects in the bucket. */
  def listAllObjects(): Future[ListResponse] = handleErrors {
    def continue(
        continuation: Option[String],
        prefix: Vector[ListResponseElement]
    ): Future[Vector[ListResponseElement]] = {
      val listRequestBuilder = ListObjectsV2Request
        .builder()
        .bucket(s3Config.bucket)

      val withContinuation = continuation
        .map { c =>
          listRequestBuilder.continuationToken(c)
        }
        .getOrElse(listRequestBuilder)

      val request = withContinuation.build()

      s3Client.listObjectsV2(request).asScala.flatMap { response =>
        val newElements = response
          .contents()
          .asScala
          .map { s3Object =>
            ListResponseElement(
              id = s3Object.key(),
              size = s3Object.size()
            )
          }
          .toVector
        val all = prefix ++ newElements
        if (response.isTruncated) {
          continue(Some(response.nextContinuationToken()), all)
        } else {
          Future.successful(all)
        }
      }
    }

    continue(None, Vector.empty).map { elements =>
      ListResponse(elements)
    }
  }

}

object S3Storage {
  case class GetFileTagsResult(
      tags: Map[String, String]
  )

  case class ListResponse(
      elements: Vector[ListResponseElement]
  )

  case class ListResponseElement(
      id: String,
      size: Long
  )
}

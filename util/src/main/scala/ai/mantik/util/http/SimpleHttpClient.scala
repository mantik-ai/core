package ai.mantik.util.http

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.HttpEntity.IndefiniteLength
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import io.circe.{ Decoder, Encoder }
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Simplified Akka Http Client for dealing with Webservices.
 * @tparam ErrorType the type to be used for parsing error responses.
 *
 * See API users for examples.
 */
class SimpleHttpClient[ErrorType: Decoder](
    rootUri: Uri,
    http: HttpExt,
    logger: Logger = LoggerFactory.getLogger(classOf[SimpleHttpClient[_]])
)(implicit ec: ExecutionContext, materializer: Materializer) extends FailFastCirceSupport {
  import SimpleHttpClient._

  type PayloadEncoder = HttpRequest => Future[HttpRequest]
  type ResponseDecoder[T] = HttpResponse => Future[T]
  type ResponseIsError = HttpResponse => Boolean

  /** The default error decoder. */
  protected def defaultErrorDecoder(httpResponse: HttpResponse): Future[ErrorType] = {
    Unmarshal(httpResponse).to[ErrorType]
  }

  /** The default detector, if a response is an error. */
  protected def defaultResponseIsErrorDetector(httpResponse: HttpResponse): Boolean = {
    !httpResponse.status.isSuccess
  }

  /** Builder, the main component for building and executing individual requests. */
  case class Builder[R](
      method: HttpMethod,
      path: Uri,
      payloadEncoder: PayloadEncoder = x => Future.successful(x),
      responseDecoder: ResponseDecoder[R],
      responseErrorDetector: ResponseIsError = x => !x.status.isSuccess,
      responseErrorDecoder: ResponseDecoder[ErrorType],
      headers: Vector[HttpHeader] = Vector.empty
  ) {

    /** Set the JSON Payload */
    def withJsonPayload[In: Encoder](in: In): Builder[R] = {
      val encoded = in.asJson.toString
      copy(
        payloadEncoder = x => Future.successful(x.withEntity(ContentTypes.`application/json`, encoded))
      )
    }

    /** Set a Binary Payload. */
    def withStreamPayload[In](source: Source[ByteString, _]): Builder[R] = {
      copy(
        payloadEncoder = x =>
          Future.successful(
            x.withEntity(HttpEntity(ContentTypes.`application/octet-stream`, source))
          )
      )
    }

    /** Set Multipart payload. */
    def withMultipartPayload(
      multipartFormDataBuilder: MultipartFormDataBuilder => MultipartFormDataBuilder
    ): Builder[R] = {
      val executedBuilder = multipartFormDataBuilder(MultipartFormDataBuilder())
      copy(
        payloadEncoder = x => {
          Future.successful(
            x.withEntity(executedBuilder.toEntity())
          )
        }
      )
    }

    /** Add Query parameters. */
    def addQuery(keyValue: (String, String)*): Builder[R] = {
      val extended: Query = Query(path.query() ++ keyValue: _*)
      copy(
        path = path.withQuery(extended)
      )
    }

    /** Set the JSON Response. */
    def withJsonResponse[Out: Decoder]: Builder[Out] = {
      copy(
        responseDecoder = { res =>
          Unmarshal(res).to[Out]
        }
      )
    }

    /** Add a Header. */
    def addHeader(name: String, value: String): Builder[R] = {
      val encoded = HttpHeader.parse(name, value) match {
        case v: ParsingResult.Ok => v.header
        case e: ParsingResult.Error =>
          throw new IllegalArgumentException(s"Invalid header ${e.errors}")
      }
      copy(headers = headers :+ encoded)
    }

    /** The full resolved Uri. */
    lazy val fullUri = path.resolvedAgainst(rootUri)

    /** Execute the request. */
    def execute(): Future[R] = {
      for {
        executed <- encodeAndExecutePlainRequest()
        parsed <- decodeResponse(executed)
      } yield parsed
    }

    /**
     * Execute the request, return the stream. Note: it must be processed.
     * (See https://doc.akka.io/docs/akka-http/current/client-side/request-level.html )
     *
     * @return Content Type and Byte Source.
     */
    def executeStream(): Future[(String, Source[ByteString, _])] = {
      for {
        executed <- encodeAndExecutePlainRequest()
      } yield {
        executed.entity.contentType.value -> executed.entity.dataBytes
      }
    }

    private def encodeAndExecutePlainRequest(): Future[HttpResponse] = {
      val t0 = System.currentTimeMillis()
      val principalResponse = for {
        prepared <- prepareRequest()
        executed <- http.singleRequest(prepared)
      } yield {
        val t1 = System.currentTimeMillis()
        logger.debug(s"${method.value} ${fullUri} took ${t1 - t0}ms, ${executed.status.intValue}")
        executed
      }
      principalResponse.flatMap {
        handleErrorResponse
      }
    }

    private def prepareRequest(): Future[HttpRequest] = {
      val baseRequest = HttpRequest(method, fullUri)
      for {
        withPayload <- payloadEncoder(baseRequest)
        withHeaders = withPayload.withHeaders(headers)
      } yield withHeaders
    }

    private def decodeResponse(response: HttpResponse): Future[R] = {
      responseDecoder(response).recover {
        case e: Throwable =>
          logger.warn(s"${method.value} ${fullUri} success response could not be parsed", e)
          throw new CouldNotDecodeResponse(response.status.intValue, response.status.reason, e)
      }
    }

    private def handleErrorResponse(response: HttpResponse): Future[HttpResponse] = {
      if (responseErrorDetector(response)) {
        responseErrorDecoder(response).recoverWith {
          case e: Throwable =>
            logger.warn(s"${method.value} ${fullUri} error result could not be parsed", e)
            throw new CouldNotDecodeResponse(response.status.intValue, response.status.reason, e)
        }.map { errorResponse =>
          throw new ParsedErrorException(response.status.intValue, errorResponse)
        }
      } else {
        Future.successful(response)
      }
    }
  }

  /** Start building a POST Request. */
  def post(path: Uri): Builder[Unit] = makeUnitRequest(HttpMethods.POST, path)

  /** Start building a GET Request. */
  def get(path: Uri): Builder[Unit] = makeUnitRequest(HttpMethods.GET, path)

  private def makeUnitRequest(method: HttpMethod, path: Uri): Builder[Unit] = {
    Builder(
      method,
      path,
      responseDecoder = _ => Future.successful(()),
      responseErrorDecoder = defaultErrorDecoder,
      responseErrorDetector = defaultResponseIsErrorDetector
    )
  }
}

object SimpleHttpClient {

  /**
   * Lookup an Akka Binary Content Type
   * @throws IllegalArgumentException if contentType is not a valid binary type.
   */
  def lookupBinaryContentType(contentType: String): ContentType.Binary = {
    ContentType.parse(contentType) match {
      case Left(errors)                  => throw new IllegalArgumentException(s"Content type ${contentType} not found, errors=${errors.map(_.summary)}")
      case Right(ok: ContentType.Binary) => ok
      case Right(different) =>
        throw new IllegalArgumentException(s"Expected binary content type, got ${different}")
    }
  }

  class SimpleHttpClientException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

  /** Exception, if an error from the server could be parsed. */
  case class ParsedErrorException[E](status: Int, message: E) extends SimpleHttpClientException(s"Parsed error ${status}, ${message}")

  /** Exception, if a response from the server could not be parsed. */
  case class CouldNotDecodeResponse(status: Int, message: String, reason: Throwable) extends SimpleHttpClientException(message, reason)
}
package ai.mantik.testutils

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, RequestEntity, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.util.ByteString

/** Add support for simple HTTP Calls. */
trait HttpSupport {
  self: TestBase with AkkaSupport =>

  protected def httpPost(url: String, contentType: String, in: ByteString): ByteString = {
    import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, Uri }
    import akka.http.scaladsl._

    logger.info(s"Accessing POST ${url}")
    val entity = await(Marshal(in).to[RequestEntity])
    val uri = Uri(url)
    val req = HttpRequest(method = HttpMethods.POST, uri = uri).withEntity(entity)
    val http = Http()
    val response = await(http.singleRequest(req))
    logger.info(s"Response to POST ${url}: ${response.status.intValue()}")
    if (response.status.isFailure()) {
      throw new RuntimeException(s"Request failed ${response.status} ${response.status.defaultMessage()}")
    }
    val content = await(Unmarshal(response).to[ByteString])
    content
  }

  protected def httpGet(url: String): (HttpResponse, ByteString) = {
    logger.info(s"Accessing HTTP Get ${url}")

    val uri = Uri(url)
    val getRequest = HttpRequest(uri = uri)
    val getResponse = await(Http().singleRequest(getRequest))
    val content = await(Unmarshal(getResponse).to[ByteString])
    (getResponse, content)
  }
}

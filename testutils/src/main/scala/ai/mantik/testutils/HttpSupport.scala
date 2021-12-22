/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.testutils

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ContentType, HttpRequest, HttpResponse, RequestEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.util.ByteString

/** Add support for simple HTTP Calls. */
trait HttpSupport {
  self: TestBase with AkkaSupport =>

  protected def httpPost(url: String, contentType: String, in: ByteString): ByteString = {
    import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
    import akka.http.scaladsl._

    logger.info(s"Accessing POST ${url}")
    val entity = await(Marshal(in).to[RequestEntity]).withContentType(ContentType.parse(contentType).forceRight)
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

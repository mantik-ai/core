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
package ai.mantik.executor.common.workerexec

import ai.mantik.componently.AkkaRuntime
import ai.mantik.testutils.{AkkaSupport, HttpSupport, TestBase}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import scala.util.Random

class LocalServerPayloadProviderSpec extends TestBase with AkkaSupport with HttpSupport {

  implicit def akkaRuntime: AkkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)

  override protected lazy val typesafeConfig: Config = ConfigFactory
    .load()
    .withValue(
      "mantik.executor.localPayloadProvider.port",
      ConfigValueFactory.fromAnyRef(0)
    )

  protected def filePrefix = "/files/"

  trait Env {
    val server = new LocalServerPayloadProvider()
  }

  protected val testBytes = ByteString {
    val bytes = new Array[Byte](1000)
    Random.nextBytes(bytes)
    bytes
  }

  // Custom Content Type

  it should "return 200 on root paths" in new Env {
    val response = await(Http().singleRequest(HttpRequest(uri = server.rootUrl())))
    response.status.intValue() shouldBe 200
    val response2 = await(Http().singleRequest(HttpRequest(uri = s"http://localhost:${server.address().port}")))
    response2.status.intValue() shouldBe 200
  }

  it should "allow file sharing" in new Env {
    val url = await(
      server.provide(
        "tag1",
        false,
        Source.single(testBytes),
        testBytes.length,
        "application/zip"
      )
    )

    url should startWith(s"${server.rootUrl()}/files/")

    val get = httpGet(url)
    get._1.status.intValue() shouldBe 200
    get._2 shouldBe testBytes

    await(server.delete("undefined"))
    val get2 = httpGet(url)
    get2._1.status.intValue() shouldBe 200
    get2._2 shouldBe testBytes

    await(server.delete("tag1"))
    val get3 = httpGet(url)
    get3._1.status.intValue() shouldBe 404
  }

  it should "know it's address" in new Env {
    val address = server.address()
    address.host shouldNot startWith("127.0.") // No loopback devices
  }

  it should "be possible to override the host" in {
    val runtime = akkaRuntime.withConfigOverride { c =>
      c.withValue("mantik.executor.localPayloadProvider.host", ConfigValueFactory.fromAnyRef("server1234"))
    }
    val server = new LocalServerPayloadProvider()(runtime)
    server.address().host shouldBe "server1234"
  }
}

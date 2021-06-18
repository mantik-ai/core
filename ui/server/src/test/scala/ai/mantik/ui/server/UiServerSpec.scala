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
package ai.mantik.ui.server

import ai.mantik.componently.AkkaRuntime
import ai.mantik.testutils.{AkkaSupport, HttpSupport, TestBase}
import ai.mantik.ui.model.{JobResponse, JobsResponse, RunGraphResponse, VersionResponse}
import akka.http.scaladsl.model.ContentTypes
import io.circe.{Decoder, Json}

class UiServerSpec extends TestBase with AkkaSupport with HttpSupport {

  private var server: UiServer = _

  val config = UiConfig(
    enabled = true,
    interface = "127.0.0.1",
    port = 0
  )

  private implicit lazy val runtime = AkkaRuntime.fromRunning()

  def address: String = s"http://127.0.0.1:${await(server.boundPort)}"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server = new UiServer(
      config,
      new DummyStateService
    )
  }

  override protected def afterAll(): Unit = {
    runtime.shutdown()
    super.afterAll()
  }

  it should "serve the main file" in {
    val (response, content) = httpGet(address)
    response.status.isSuccess() shouldBe true
    content shouldNot be(empty)
  }

  it should "serve assets" in {
    val (response, content) = httpGet(address + "/favicon.ico")
    response.status.isSuccess() shouldBe true
    content shouldNot be(empty)
  }

  it should "server unknown regular files" in {
    val (response, _) = httpGet(address + "/not_existing")
    response.status.intValue() shouldBe 200
  }

  it should "not serve unknown assetss" in {
    val (response, _) = httpGet(address + "/not_existing.js")
    response.status.intValue() shouldBe 404
  }

  it should "be possible to deactivate" in {
    val server2 = new UiServer(config.copy(enabled = false), new DummyStateService)
    awaitException[IllegalStateException](server2.boundPort)
  }

  it should "serve the API infos" in {
    def testJson[T: Decoder](path: String): Unit = {
      val (response, content) = httpGet(address + path)
      response.status.intValue() shouldBe 200
      response.entity.contentType shouldBe ContentTypes.`application/json`
      io.circe.parser.parse(content.utf8String).forceRight.as[T].forceRight
    }
    testJson[JobsResponse]("/api/jobs")
    testJson[VersionResponse]("/api/version")
    testJson[JobResponse](s"/api/jobs/${DummyStateService.jobId}")

    val (response, _) = httpGet(address + "/api/jobs/otherjob")
    response.status.intValue() shouldBe 404
    response.entity.contentType shouldBe ContentTypes.`application/json`

    testJson[RunGraphResponse](s"/api/jobs/${DummyStateService.jobId}/operations/1,2,3/graph")
  }

  it should "block unknown API calls" in {
    val (response, _) = httpGet(address + "/api/not_existing")
    response.status.intValue() shouldBe 404
  }
}

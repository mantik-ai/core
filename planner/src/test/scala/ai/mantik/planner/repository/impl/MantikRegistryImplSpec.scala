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
package ai.mantik.planner.repository.impl

import ai.mantik.ds.FundamentalType
import ai.mantik.elements.{DataSetDefinition, ItemId, MantikHeader, NamedMantikId}
import ai.mantik.elements.registry.api.{
  ApiFileUploadResponse,
  ApiLoginRequest,
  ApiLoginResponse,
  ApiPrepareUploadResponse,
  MantikRegistryApi,
  MantikRegistryApiCalls
}
import ai.mantik.planner.repository.{Bridge, ContentTypes, MantikArtifact}
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.TestBase
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import net.reactivecore.fhttp.akka.{ApiServer, ApiServerRoute}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher

import scala.language.reflectiveCalls
import scala.concurrent.Future

class MantikRegistryImplSpec extends TestBaseWithAkkaRuntime {

  private val dummyPort = 9005

  override protected lazy val typesafeConfig: Config = ConfigFactory
    .load()
    .withValue(
      "mantik.core.registry.url",
      ConfigValueFactory.fromAnyRef(s"http://localhost:${dummyPort}")
    )

  trait Env {
    val apiRoute = new ApiServerRoute {
      var gotLogin: ApiLoginRequest = _

      bind(MantikRegistryApiCalls.uploadFile).to { case (token, itemId, contentType, content) =>
        Future.successful(
          Right(ApiFileUploadResponse("file1"))
        )
      }

      bind(MantikRegistryApiCalls.prepareUpload).to { case (token, request) =>
        Future.successful(Right(ApiPrepareUploadResponse(Some(10))))
      }

      bind(MantikRegistryApiCalls.login).to { request =>
        gotLogin = request
        Future.successful(Right(ApiLoginResponse("Token1234", None)))
      }
    }
    val fullFakeRoute = pathPrefix("api") { apiRoute }
    val server = new ApiServer(fullFakeRoute, port = dummyPort)
    akkaRuntime.lifecycle.addShutdownHook {
      server.close()
    }
    val client = new MantikRegistryImpl()
  }

  it should "get a nice token" in new Env {
    await(client.token()) shouldBe "Token1234"
  }

  it should "not crash on empty chunks" in new Env {
    // Workaround Akka http crashes on generating empty chunks for body parts
    // MantikRegistryClient must filter them out.
    val emptyData = Source(List(ByteString.fromString("a"), ByteString(), ByteString.fromString("c")))
    val mantikHeader = MantikHeader.pure(
      DataSetDefinition(
        bridge = Bridge.naturalBridge.mantikId,
        `type` = FundamentalType.Int32
      )
    )
    val response = await(
      client.addMantikArtifact(
        MantikArtifact(
          mantikHeader.toJson,
          fileId = None,
          namedId = Some(NamedMantikId("hello_world")),
          itemId = ItemId.generate(),
          deploymentInfo = None
        ),
        payload = Some(ContentTypes.ZipFileContentType -> emptyData)
      )
    )
    response.fileId shouldBe Some("file1")
  }

  it should "do a custom login" in new Env {
    val response = await(
      client.login(
        s"http://localhost:${dummyPort}",
        "username1",
        "password1"
      )
    )
    apiRoute.gotLogin.name shouldBe "username1"
    apiRoute.gotLogin.password shouldBe "password1"
    response.token shouldBe "Token1234"
    response.validUntil shouldBe empty

    awaitException[RuntimeException] {
      client.login(
        s"http://localhost:100", // unused port
        "username1",
        "password1"
      )
    }
  }
}

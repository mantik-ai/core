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
package ai.mantik.engine.server.services

import java.time.Instant
import ai.mantik.componently.rpc.{RpcConversions, StreamConversions}
import ai.mantik.ds.FundamentalType.{Int32, StringType}
import ai.mantik.ds.functional.FunctionType
import ai.mantik.elements.{
  AlgorithmDefinition,
  DataSetDefinition,
  ItemId,
  MantikDefinition,
  MantikHeader,
  MantikId,
  NamedMantikId
}
import ai.mantik.engine.protos.local_registry.{
  AddArtifactRequest,
  AddArtifactResponse,
  GetArtifactRequest,
  GetArtifactWithPayloadResponse,
  ListArtifactsRequest,
  TagArtifactRequest
}
import ai.mantik.engine.testutil.TestBaseWithSessions
import ai.mantik.planner.repository.{ContentTypes, DeploymentInfo, MantikArtifact, SubDeploymentInfo}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.protobuf.timestamp.Timestamp
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException

import scala.util.Random

class LocalRegistryServiceImplSpec extends TestBaseWithSessions {

  trait EnvBase {
    val localRegistryServiceImpl = new LocalRegistryServiceImpl(components.localRegistry)
    val localRepo = components.repository

    val item = MantikArtifact(
      MantikHeader
        .pure(
          AlgorithmDefinition(bridge = "some_stack", `type` = FunctionType(Int32, Int32))
        )
        .toJson,
      fileId = Some("1234"),
      namedId = Some("item1"),
      itemId = ItemId.generate()
    )

    val item2 = item.copy(
      mantikHeader = MantikHeader
        .pure(
          DataSetDefinition(bridge = "format", `type` = StringType)
        )
        .toJson,
      itemId = ItemId.generate(),
      namedId = None
    )

    val item3 = item.copy(
      deploymentInfo = Some(
        DeploymentInfo(
          name = "abcd",
          internalUrl = "internal1",
          externalUrl = Some("external1"),
          timestamp = Instant.parse("2019-09-19T11:18:24.123Z"), // Note: database has only milliseconds precision
          sub = Map(
            "a" -> SubDeploymentInfo(
              name = "subA",
              internalUrl = "subAInternal"
            )
          )
        )
      ),
      itemId = ItemId.generate(),
      namedId = Some("item3")
    )
  }

  trait Env extends EnvBase {
    await(localRepo.store(item))
    await(localRepo.store(item2))
    await(localRepo.store(item3))
  }

  "get" should "should fail for not existing" in new Env {
    val e = awaitException[StatusRuntimeException] {
      localRegistryServiceImpl.getArtifact(
        GetArtifactRequest("notexisting")
      )
    }
    e.getStatus.getCode shouldBe Code.NOT_FOUND
  }

  it should "return a regular element" in new Env {
    val back = Converters.decodeMantikArtifact(
      await(
        localRegistryServiceImpl.getArtifact(
          GetArtifactRequest(item.namedId.get.toString)
        )
      ).artifact.get
    )
    back shouldBe item

    withClue("It should work with item ids") {
      val got = await(localRegistryServiceImpl.getArtifact(GetArtifactRequest(item.itemId.toString)))
      Converters.decodeMantikArtifact(got.artifact.get) shouldBe back.copy(
        namedId = None
      )
    }
  }

  it should "return a deployed element" in new Env {
    val back = await(
      localRegistryServiceImpl.getArtifact(
        GetArtifactRequest("item3")
      )
    ).artifact.get.deploymentInfo.get

    back.name shouldBe "abcd"
    back.internalUrl shouldBe "internal1"
    back.externalUrl shouldBe "external1"
    RpcConversions.decodeInstant(Timestamp.toJavaProto(back.timestamp.get)) shouldBe
      item3.deploymentInfo.get.timestamp
  }

  it should "list items" in new Env {
    await(
      localRegistryServiceImpl.listArtifacts(
        ListArtifactsRequest()
      )
    ).artifacts.map(Converters.decodeMantikArtifact) should contain theSameElementsAs Seq(
      item,
      item3
    )
    await(
      localRegistryServiceImpl.listArtifacts(
        ListArtifactsRequest(anonymous = true)
      )
    ).artifacts.map(Converters.decodeMantikArtifact) should contain theSameElementsAs Seq(
      item,
      item2,
      item3
    )
    await(
      localRegistryServiceImpl.listArtifacts(
        ListArtifactsRequest(kind = MantikDefinition.DataSetKind, anonymous = true)
      )
    ).artifacts.map(Converters.decodeMantikArtifact) should contain theSameElementsAs Seq(
      item2
    )
  }

  "tagArtifact" should "tag artifacts" in new Env {
    val response = await(
      localRegistryServiceImpl.tagArtifact(
        TagArtifactRequest(
          item.itemId.toString,
          "foobar"
        )
      )
    )
    response.changed shouldBe true
    await(localRepo.get(NamedMantikId("foobar"))).itemId shouldBe item.itemId

    val response2 = await(
      localRegistryServiceImpl.tagArtifact(
        TagArtifactRequest(
          item.itemId.toString,
          "foobar"
        )
      )
    )
    response2.changed shouldBe false
  }

  "add" should "add elements without payload" in new EnvBase {
    val (resultObserver, resultFuture) = StreamConversions.singleStreamObserverFuture[AddArtifactResponse]()
    val requestObserver = localRegistryServiceImpl.addArtifact(resultObserver)
    requestObserver.onNext(
      AddArtifactRequest(
        mantikHeader = item.mantikHeader
      )
    )
    requestObserver.onCompleted()
    val result = await(resultFuture)

    val original = await(localRepo.get(ItemId(result.artifact.get.itemId)))
    Converters.decodeMantikArtifact(result.artifact.get) shouldBe original
  }

  it should "keep YAML code in mantikHeaders" in new EnvBase {
    val (resultObserver, resultFuture) = StreamConversions.singleStreamObserverFuture[AddArtifactResponse]()
    val requestObserver = localRegistryServiceImpl.addArtifact(resultObserver)
    val mantikHeader =
      """kind: algorithm
        |bridge: foobar
        |# This is a comment which should survive
        |type:
        |  input: int32
        |  output: int32
        |""".stripMargin
    requestObserver.onNext(
      AddArtifactRequest(
        mantikHeader = mantikHeader
      )
    )
    requestObserver.onCompleted()
    val result = await(resultFuture)

    val stored = await(localRepo.get(ItemId(result.artifact.get.itemId)))
    Converters.decodeMantikArtifact(result.artifact.get) shouldBe stored
    stored.mantikHeader shouldBe mantikHeader
    result.artifact.get.mantikHeader shouldBe mantikHeader
    result.artifact.get.mantikHeaderJson shouldBe stored.parsedMantikHeader.toJson

    val getResult = await(localRegistryServiceImpl.getArtifact(GetArtifactRequest(result.artifact.get.itemId)))
    getResult.artifact.get.mantikHeader shouldBe mantikHeader
    getResult.artifact.get.mantikHeaderJson shouldBe stored.parsedMantikHeader.toJson
  }

  it should "support naming the artifact" in new EnvBase {
    val (resultObserver, resultFuture) = StreamConversions.singleStreamObserverFuture[AddArtifactResponse]()
    val requestObserver = localRegistryServiceImpl.addArtifact(resultObserver)
    requestObserver.onNext(
      AddArtifactRequest(
        mantikHeader = item.mantikHeader,
        namedMantikId = "name1"
      )
    )
    requestObserver.onCompleted()
    val result = await(resultFuture)

    val original = await(localRepo.get(NamedMantikId(result.artifact.get.namedId)))
    Converters.decodeMantikArtifact(result.artifact.get) shouldBe original
    original.namedId shouldBe Some(NamedMantikId("name1"))
  }

  it should "add elements with payload" in new EnvBase {
    val bytes = Seq(
      ByteString("blob1"),
      ByteString("blob2"),
      ByteString("blob3")
    )
    val (resultObserver, resultFuture) = StreamConversions.singleStreamObserverFuture[AddArtifactResponse]()
    val requestObserver = localRegistryServiceImpl.addArtifact(resultObserver)
    requestObserver.onNext(
      AddArtifactRequest(
        mantikHeader = item.mantikHeader,
        contentType = ContentTypes.ZipFileContentType,
        payload = RpcConversions.encodeByteString(bytes.head)
      )
    )
    bytes.tail.foreach { b =>
      requestObserver.onNext(AddArtifactRequest(payload = RpcConversions.encodeByteString(b)))
    }
    requestObserver.onCompleted()
    val result = await(resultFuture)

    val original = await(localRepo.get(ItemId(result.artifact.get.itemId)))
    Converters.decodeMantikArtifact(result.artifact.get) shouldBe original

    original.fileId shouldBe defined
    val loadFileResult = await(components.fileRepository.loadFile(original.fileId.get))
    loadFileResult.contentType shouldBe ContentTypes.ZipFileContentType
    val fileContent = collectByteSource(loadFileResult.source)
    fileContent shouldBe (bytes.reduce(_ ++ _))
  }

  it should "take over the name of the mantik file if no alternative is given" in new EnvBase {
    val (resultObserver, resultFuture) = StreamConversions.singleStreamObserverFuture[AddArtifactResponse]()
    val requestObserver = localRegistryServiceImpl.addArtifact(resultObserver)

    val namedId = NamedMantikId(
      "account/my_nice_algorithm:1.1"
    )

    namedId.violations shouldBe empty

    val myItem = item.copy(
      mantikHeader = item.parsedMantikHeader
        .withMantikHeaderMeta(
          item.parsedMantikHeader.header.withId(
            namedId
          )
        )
        .toJson
    )

    requestObserver.onNext(
      AddArtifactRequest(
        mantikHeader = myItem.mantikHeader
      )
    )
    requestObserver.onCompleted()
    val result = await(resultFuture)
    result.getArtifact.namedId shouldBe namedId.toString

    val original = await(localRepo.get(MantikId.fromString(result.artifact.get.namedId)))
    Converters.decodeMantikArtifact(result.artifact.get) shouldBe original
    original.namedId shouldBe Some(namedId)
  }

  "getArtifactWithPayload" should "deliver artifacts without payload" in new EnvBase {
    val itemWithoutFile = item.copy(fileId = None)
    await(localRepo.store(itemWithoutFile))

    val (resultObserver, resultFuture) = StreamConversions.singleStreamObserverFuture[GetArtifactWithPayloadResponse]()
    localRegistryServiceImpl.getArtifactWithPayload(
      GetArtifactRequest(itemWithoutFile.namedId.get.toString),
      resultObserver
    )
    val result = await(resultFuture)
    val back = Converters.decodeMantikArtifact(result.artifact.get)
    back shouldBe itemWithoutFile
    result.contentType shouldBe ""
    result.payload.isEmpty shouldBe true
  }

  it should "deliver artifacts with payload" in new EnvBase {
    val bytes = {
      val b = new Array[Byte](100000)
      Random.nextBytes(b)
      ByteString(b)
    }

    val itemToUse = await(
      components.localRegistry.addMantikArtifact(
        item,
        Some(
          ContentTypes.ZipFileContentType -> Source.single(bytes)
        )
      )
    )

    val (streamObserver, future) = StreamConversions.streamObserverCollector[GetArtifactWithPayloadResponse]()
    localRegistryServiceImpl.getArtifactWithPayload(
      GetArtifactRequest(itemToUse.namedId.get.toString),
      streamObserver
    )
    val parts = await(future)
    parts.size shouldBe >=(2)
    Converters.decodeMantikArtifact(parts.head.artifact.get) shouldBe itemToUse
    parts.head.contentType shouldBe ContentTypes.ZipFileContentType
    val collectedBytes = parts.map(p => RpcConversions.decodeByteString(p.payload)).reduce(_ ++ _)
    collectedBytes shouldBe bytes
  }
}

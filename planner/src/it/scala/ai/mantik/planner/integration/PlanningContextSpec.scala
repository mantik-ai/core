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
package ai.mantik.planner.integration

import java.net.InetSocketAddress
import java.nio.file.{Path, Paths}
import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.elements.NamedMantikId
import ai.mantik.elements.errors.{ErrorCodes, MantikException}
import ai.mantik.planner.impl.{RemotePlanningContextImpl, RemotePlanningContextServerImpl}
import ai.mantik.planner.protos.planning_context.PlanningContextServiceGrpc.{
  PlanningContextService,
  PlanningContextServiceStub
}
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.{DataSet, MantikItem, PlanningContext}
import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.grpc.netty.NettyServerBuilder

abstract class PlanningContextSpecBase extends IntegrationTestBase {

  protected def planningContext: PlanningContext

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    context.pushLocalMantikItem(Paths.get("bridge/binary"))
  }

  "load" should "load an mantik item" in {
    intercept[MantikException] {
      planningContext.loadDataSet("loadTest1")
    }.code shouldBe ErrorCodes.MantikItemNotFound

    planningContext.execute(
      DataSet
        .literal(
          Bundle.fundamental(10)
        )
        .tag("loadTest1")
        .save()
    )

    val got = planningContext.loadDataSet("loadTest1")
    got.mantikId shouldBe NamedMantikId("loadTest1")

    val got2 = planningContext.load("loadTest1")
    got2 shouldBe got

    def checkBadType(f: (PlanningContext, String) => MantikItem): Unit = {
      intercept[MantikException] {
        f(planningContext, "loadTest1")
      }.code shouldBe ErrorCodes.MantikItemWrongType
    }
    checkBadType(_.loadPipeline(_))
    checkBadType(_.loadAlgorithm(_))
    checkBadType(_.loadTrainableAlgorithm(_))
  }

  "pull" should "pull an item from a remote repository" in {
    // can't test this easily without a working remote repo
    pending
  }

  "execute" should "execute an operation" in {
    val simpleInput = DataSet.literal(
      TabularBundle.buildColumnWise
        .withPrimitives(
          "x",
          1,
          2,
          3
        )
        .result
    )
    val increased = simpleInput.select("select x + 1 as y")
    val got = planningContext.execute(increased.fetch)
    got shouldBe TabularBundle.buildColumnWise
      .withPrimitives(
        "y",
        2,
        3,
        4
      )
      .result
  }

  it should "handle async errors" in {
    val simpleInput = DataSet.literal(
      TabularBundle.buildColumnWise
        .withPrimitives(
          "x",
          "1",
          "2",
          "Bad Data"
        )
        .result
    )
    val converted = simpleInput.select("select cast (x AS INT32)")
    intercept[MantikException] {
      planningContext.execute(converted.fetch)
    }
  }

  "pushLocalItem" should "add a local item and use it's default name" in {
    intercept[MantikException] {
      planningContext.loadDataSet("mnist_dataset")
    }.code shouldBe ErrorCodes.MantikItemNotFound

    val name = planningContext.pushLocalMantikItem(Paths.get("bridge/binary/test/mnist"))
    name shouldBe NamedMantikId("mnist_test")

    withClue("No exception expected") {
      planningContext.loadDataSet("mnist_test")
    }
  }

  it should "use the supplied name if given" in {
    intercept[MantikException] {
      planningContext.loadDataSet("foo")
    }.code shouldBe ErrorCodes.MantikItemNotFound

    val name = planningContext.pushLocalMantikItem(Paths.get("bridge/binary/test/mnist"), Some("foo"))
    name shouldBe NamedMantikId("foo")

    withClue("No exception expected") {
      planningContext.loadDataSet("foo")
    }
  }

  trait EnvForFiles {
    val sampleBytes = Seq(ByteString("Hello"), ByteString(" World"))
    val sampleSource = Source(sampleBytes)
    val expectedLength = sampleBytes.map(_.length).sum
  }

  "storeFile" should "store a file in repo" in new EnvForFiles {
    val (fileId, size) = planningContext.storeFile(
      ContentTypes.Csv,
      sampleSource,
      temporary = false
    )
    fileId shouldNot be(empty)
    size shouldBe expectedLength
    val file = await(fileRepository.requestFileGet(fileId))
    file.fileId shouldBe fileId
    file.fileSize shouldBe Some(expectedLength)
    file.contentType shouldBe ContentTypes.Csv
    file.isTemporary shouldBe false
  }

  it should "work with temporary files" in new EnvForFiles {
    val (fileId, size) = planningContext.storeFile(
      ContentTypes.Csv,
      sampleSource,
      temporary = true
    )
    fileId shouldNot be(empty)
    size shouldBe expectedLength
    val file = await(fileRepository.requestFileGet(fileId))
    file.fileId shouldBe fileId
    file.fileSize shouldBe Some(expectedLength)
    file.contentType shouldBe ContentTypes.Csv
    file.isTemporary shouldBe true
  }

  it should "work with known file size" in new EnvForFiles {
    val (fileId, size) = planningContext.storeFile(
      ContentTypes.Csv,
      sampleSource,
      temporary = false,
      contentLength = Some(expectedLength)
    )
    size shouldBe expectedLength
    val file = await(fileRepository.requestFileGet(fileId))
    file.fileId shouldBe fileId
    file.fileSize shouldBe Some(expectedLength)
  }

  it should "fail if the expected size doesn't match" in new EnvForFiles {
    intercept[MantikException] {
      planningContext.storeFile(
        ContentTypes.Csv,
        sampleSource,
        temporary = false,
        contentLength = Some(123)
      )
    }.code shouldBe ErrorCodes.BadFileSize
  }
}

class PlanningContextSpec extends PlanningContextSpecBase {
  override protected def planningContext: PlanningContext = context
}

class RemotePlanningContextSpec extends PlanningContextSpecBase {
  private lazy val service = new RemotePlanningContextServerImpl(
    context,
    retriever
  )

  private lazy val grpcServer = {
    val server = NettyServerBuilder
      .forAddress(new InetSocketAddress("localhost", 0))
      .addService(PlanningContextService.bindService(service, ec))
      .build()

    server.start()
    server
  }

  private lazy val channel: ManagedChannel =
    ManagedChannelBuilder.forAddress("localhost", grpcServer.getPort).usePlaintext().build()

  override lazy val planningContext: PlanningContext = {
    val remoteContextStub = new PlanningContextServiceStub(channel)
    val remoteContext = new RemotePlanningContextImpl(remoteContextStub)
    remoteContext
  }

  override protected def afterAll(): Unit = {
    channel.shutdown()
    grpcServer.shutdownNow()
    super.afterAll()
  }
}

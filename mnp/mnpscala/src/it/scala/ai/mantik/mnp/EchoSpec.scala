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
package ai.mantik.mnp

import java.io.File

import ai.mantik.componently.utils.GlobalLocalAkkaRuntime
import ai.mantik.mnp.protocol.mnp.{ConfigureInputPort, ConfigureOutputPort, QuitRequest, SessionState}
import ai.mantik.testutils.{AkkaSupport, TestBase}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.sys.process.Process

class EchoSpec extends TestBase with AkkaSupport with GlobalLocalAkkaRuntime {

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    enterTestcase()
  }

  override protected def afterEach(): Unit = {
    exitTestcase()
    super.afterEach()
  }

  val EchoServerExecutable = "./mnp/mnpgo/target/mnpgo"
  private var echoProcess: Process = null

  val HelloWorld = ByteString("HelloWorld")
  val Port = 18502

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    new File(EchoServerExecutable).exists() shouldBe true
    import sys.process._
    echoProcess = Seq(EchoServerExecutable, "echo", "--port", Port.toString).run()
    Thread.sleep(100)
    echoProcess.isAlive() shouldBe true
  }

  override protected def afterAll(): Unit = {
    echoProcess.destroy()
  }

  trait Env {
    def withClient[T](f: MnpClient => T): T = {
      val (channel, client) = MnpClient.connect(s"localhost:${Port}")
      try {
        f(client)
      } finally {
        channel.shutdownNow()
      }
    }
  }

  it should "work for a trivial calls" in new Env {
    withClient { client =>
      val aboutResponse = await(client.about())
      aboutResponse.name shouldBe "EchoHandler"

      withClue("It should not throw") {
        await(client.quit())
      }
    }
  }

  it should "do a simple data ping" in new Env {
    withClient { client =>
      val states = Vector.newBuilder[SessionState]
      val callback: SessionState => Unit = { s =>
        states += s
      }

      val session = await(
        client.initSession(
          "session1",
          None,
          List(ConfigureInputPort("content1")),
          List(ConfigureOutputPort("content2")),
          callback
        )
      )

      states.result() shouldBe Vector(SessionState.SS_INITIALIZING, SessionState.SS_STARTING_UP)

      val task = session.task("task1")

      val sink1 = task.push(0)
      val result = Source.single(HelloWorld).runWith(sink1)

      val source1 = task.pull(0)
      val collected = collectByteSource(source1)
      collected shouldBe HelloWorld

      val (bytes, response) = await(result)
      bytes shouldBe HelloWorld.size

      val quitResponse = await(session.quit())
    }
  }

  it should "proper fail on bad session init" in new Env {
    withClient { client =>
      val response = client.initSession(
        "badSession",
        None,
        List(ConfigureInputPort("c1")),
        List(ConfigureOutputPort("r1"), ConfigureOutputPort("r2"))
      )
      intercept[SessionInitException] {
        await(response)
      }
    }
  }

  it should "do a mor complex scenario" in new Env {
    withClient { client =>
      val session = await(
        client.initSession(
          "session1",
          None,
          List(ConfigureInputPort("content1"), ConfigureInputPort("content2")),
          List(ConfigureOutputPort("reverseContent1"), ConfigureOutputPort("reverseContent2"))
        )
      )

      val task = session.task("task2")

      val sink1 = task.push(0)
      sink1.runWith(Source.single(HelloWorld))

      val sink2 = task.push(1)

      val data2: IndexedSeq[ByteString] = for (i <- 0 until 100) yield {
        ByteString(s"A lot of data ${i}")
      }
      val slowData2Iterator = data2.map { b =>
        Thread.sleep(10)
        b
      }.iterator

      val source1 = task.pull(0)
      val source2 = task.pull(1)

      val responseFuture = Source.fromIterator(() => slowData2Iterator).runWith(sink2)

      collectByteSource(source1) shouldBe HelloWorld
      collectByteSource(source2) shouldBe data2.reduce(_ ++ _)

      val (bytes, response) = await(responseFuture)
      bytes shouldBe data2.foldLeft(0L)(_ + _.length)
    }
  }
}

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

import ai.mantik.mnp.protocol.mnp.{ ConfigureInputPort, ConfigureOutputPort, TaskState }
import ai.mantik.mnp.server.MnpServer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, TimeoutException }
import scala.util.control.NonFatal

class MnpServerClientSpec extends TestBaseWithAkkaRuntime {

  trait Env {
    val backend = new DummyBackend()
    val server = new MnpServer(backend)
    server.start()
    val (channel, client) = MnpClient.connect(s"127.0.0.1:${server.port}")
    akkaRuntime.lifecycle.addShutdownHook {
      Future {
        channel.shutdown()
      }
    }
  }

  private def shortAwait[T](f: Future[T]): T = Await.result(f, 50.millis)

  it should "send about call" in new Env {
    val response = await(client.about())
    response.name shouldBe "DummyBackend"
  }

  it should "quit when requested" in new Env {
    backend.quitted shouldBe false
    server.isShutdown shouldBe false
    await(client.quit())
    backend.quitted shouldBe true
    server.isShutdown shouldBe true
  }

  val simpleInput = Seq(ConfigureInputPort("type1"))
  val simpleOutput = Seq(ConfigureOutputPort("type1"))

  it should "open and close a session" in new Env {
    val session = await(client.initSession(
      "session1",
      config = None,
      inputs = simpleInput,
      outputs = simpleOutput
    ))
    val backendSession = backend.getSession("session1").get
    backendSession.sessionQuitted shouldBe false

    awaitException[Exception] {
      client.initSession("session1", config = None, inputs = simpleInput, outputs = simpleOutput)
    }.getMessage should include("Session already exists")

    backendSession.sessionQuitted shouldBe false

    await(session.quit())

    backendSession.sessionQuitted shouldBe true
  }

  it should "execute a trivial echo session" in new Env {
    val session = await(client.initSession(
      "session1",
      config = None,
      inputs = simpleInput,
      outputs = simpleOutput
    ))

    val task = session.task("task1")
    task.push(0).runWith(Source(Vector(ByteString("Hello"), ByteString("World"), ByteString(" How are you?"))))

    eventually {
      val queryInfo1 = await(task.query(false))
      val state = queryInfo1.state
      state should (be(TaskState.TS_EXISTS) or be(TaskState.TS_FINISHED))
    }

    val got = task.pull(0)
    collectByteSource(got) shouldBe ByteString("HelloWorld How are you?")

    val queryInfo2 = await(task.query(false))
    queryInfo2.state shouldBe TaskState.TS_FINISHED
    queryInfo2.inputs(0).data shouldBe 23
    queryInfo2.inputs(0).msgCount shouldBe 4 // With header

    queryInfo2.outputs(0).data shouldBe 23
    queryInfo2.outputs(0).msgCount shouldBe >=(1)

  }

  it should "create a task with query task" in new Env {
    val session = await(client.initSession(
      "session1",
      config = None,
      inputs = simpleInput,
      outputs = simpleOutput
    ))

    val task = session.task("task1")
    awaitException[RuntimeException] {
      task.query(false)
    }.getMessage should include("not found")

    val queryResponse = await(task.query(true))
    queryResponse.state shouldBe TaskState.TS_EXISTS

    val query2 = await(task.query(false))
    query2 shouldBe queryResponse
  }

  it should "handle a crashing session" in new Env {
    val exception = new RuntimeException("BOOM")
    backend.sessionInitCrash = Some(exception)
    awaitException[Exception](client.initSession(
      "session1",
      config = None,
      inputs = simpleInput,
      outputs = simpleOutput
    )).getMessage should include("BOOM")
  }

  it should "handle a crashing task" in new Env {
    val exception = new RuntimeException("BOOM")
    val session = await(client.initSession(
      "session1",
      config = None,
      inputs = simpleInput,
      outputs = simpleOutput
    ))

    backend.getSession("session1").get.crashingTask = Some(exception)

    val task = session.task("task1")
    await(task.query(true))

    task.push(0).runWith(Source(Vector(ByteString("Hello"))))

    eventually {
      val taskQuery = await(task.query(false))
      taskQuery.state shouldBe TaskState.TS_FAILED
      taskQuery
    }.error should include("BOOM")
  }

  it should "wait with collecting" in new Env {
    val session = await(client.initSession(
      "session1",
      config = None,
      inputs = simpleInput,
      outputs = simpleOutput
    ))
    val task = session.task("task1")
    intercept[TimeoutException] {
      shortAwait(task.pull(0).runWith(Sink.seq))
    }
  }

  trait EnvWithTwoServers extends Env {
    val backend2 = new DummyBackend()
    val server2 = new MnpServer(backend2)
    server2.start()
    val (channel2, client2) = MnpClient.connect(s"127.0.0.1:${server2.port}")
    akkaRuntime.lifecycle.addShutdownHook {
      Future {
        channel2.shutdown()
      }
    }
  }

  it should "handle automatic forwarding" in new EnvWithTwoServers {
    val session1 = await(client.initSession(
      "session1",
      config = None,
      inputs = simpleInput,
      outputs = Seq(ConfigureOutputPort("type1", s"mnp://127.0.0.1:${server2.port}/session2/0"))
    ))
    val session2 = await(client2.initSession(
      "session2",
      config = None,
      inputs = simpleInput,
      outputs = simpleOutput
    ))
    val inTask = session1.task("task1")
    val outTask = session2.task("task1")
    inTask.push(0).runWith(Source(Vector(ByteString("Hello "), ByteString("World"))))
    val got = collectByteSource(outTask.pull(0))
    got shouldBe ByteString("Hello World")

    await(inTask.query(false)).state shouldBe TaskState.TS_FINISHED
    await(outTask.query(false)).state shouldBe TaskState.TS_FINISHED
  }
}

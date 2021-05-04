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
package ai.mantik.engine.session

import ai.mantik.elements.errors.MantikException
import ai.mantik.testutils.{AkkaSupport, TestBase}

class SessionManagerSpec extends TestBase with AkkaSupport {

  class DummySession(val id: String) extends SessionBase {
    var isShutdown = false
    override def quitSession(): Unit = {
      isShutdown = true
    }
  }

  it should "provide create/get/close mechanisms" in {
    val manager = new SessionManagerBase[DummySession](id => new DummySession(id))
    val session = await(manager.create())
    val session2 = await(manager.get(session.id))
    session2 shouldBe session

    val session3 = await(manager.create())
    session3.id shouldNot be(session.id)

    session.isShutdown shouldBe false
    await(manager.close(session.id))
    session.isShutdown shouldBe true
    awaitException[MantikException] {
      manager.get(session.id)
    }.code.isA(EngineErrors.SessionNotFound) shouldBe true

    session3.isShutdown shouldBe false
  }

  it should "automatically timeout sessions" in {
    pending
  }
}

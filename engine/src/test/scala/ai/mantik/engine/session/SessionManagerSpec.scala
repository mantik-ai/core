package ai.mantik.engine.session

import ai.mantik.testutils.{ AkkaSupport, TestBase }

class SessionManagerSpec extends TestBase with AkkaSupport {

  class DummySession(val id: String) extends SessionBase {
    var isShutdown = false
    override def shutdown(): Unit = {
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
    intercept[SessionNotFoundException] {
      await(manager.get(session.id))
    }

    session3.isShutdown shouldBe false
  }

  it should "automatically timeout sessions" in {
    pending
  }
}

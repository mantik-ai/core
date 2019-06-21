package ai.mantik.engine.testutil

import ai.mantik.engine.session.{ Session, SessionManager }

abstract class TestBaseWithSessions extends TestBaseWithAkkaRuntime {

  protected var components: DummyComponents = _
  protected var sessionManager: SessionManager[Session] = _

  override protected def beforeEach(): Unit = {
    components = new DummyComponents()
    sessionManager = new SessionManager[Session]({ id =>
      new Session(id, components.shared())
    })
  }

  override protected def afterEach(): Unit = {
    components.shutdown()
  }
}

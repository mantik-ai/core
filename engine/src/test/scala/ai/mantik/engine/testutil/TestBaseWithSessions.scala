package ai.mantik.engine.testutil

import ai.mantik.engine.session.{ Session, SessionManager }

abstract class TestBaseWithSessions extends TestBaseWithAkkaRuntime {

  protected var components: DummyComponents = _
  protected var sessionManager: SessionManager = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    components = new DummyComponents()
    sessionManager = new SessionManager({ id =>
      new Session(id, components.shared())
    })
  }
}

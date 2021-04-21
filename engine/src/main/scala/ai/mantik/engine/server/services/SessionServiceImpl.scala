package ai.mantik.engine.server.services

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.engine.protos.sessions.{
  CloseSessionRequest,
  CloseSessionResponse,
  CreateSessionRequest,
  CreateSessionResponse
}
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionService
import ai.mantik.engine.session.{Session, SessionBase, SessionManager}
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

class SessionServiceImpl @Inject() (sessionManager: SessionManager)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with SessionService
    with RpcServiceBase {

  override def createSession(request: CreateSessionRequest): Future[CreateSessionResponse] = handleErrors {
    sessionManager.create().map { session =>
      CreateSessionResponse(session.id)
    }
  }

  override def closeSession(request: CloseSessionRequest): Future[CloseSessionResponse] = handleErrors {
    sessionManager.close(request.sessionId).map { _ =>
      CloseSessionResponse()
    }
  }
}

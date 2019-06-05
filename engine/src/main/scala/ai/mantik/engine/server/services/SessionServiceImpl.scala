package ai.mantik.engine.server.services

import ai.mantik.engine.protos.sessions.{ CloseSessionRequest, CloseSessionResponse, CreateSessionRequest, CreateSessionResponse }
import ai.mantik.engine.protos.sessions.SessionServiceGrpc.SessionService
import ai.mantik.engine.session.{ Session, SessionBase, SessionManager }

import scala.concurrent.{ ExecutionContext, Future }

class SessionServiceImpl(sessionManager: SessionManager[_ <: SessionBase])(implicit ec: ExecutionContext) extends SessionService {

  override def createSession(request: CreateSessionRequest): Future[CreateSessionResponse] = {
    sessionManager.create().map { session =>
      CreateSessionResponse(session.id)
    }
  }

  override def closeSession(request: CloseSessionRequest): Future[CloseSessionResponse] = {
    sessionManager.close(request.sessionId).map { _ =>
      CloseSessionResponse()
    }
  }
}

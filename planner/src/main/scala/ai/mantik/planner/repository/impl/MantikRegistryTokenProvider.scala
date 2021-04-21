package ai.mantik.planner.repository.impl

import java.time.Instant

import ai.mantik.componently.utils.SecretReader
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.elements.registry.api._
import ai.mantik.planner.buildinfo.BuildInfo
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Provides tokens for talking to the mantik registry */
class MantikRegistryTokenProvider(
    registryApi: MantikRegistryApi,
    user: String,
    password: SecretReader
)(
    implicit akkaRuntime: AkkaRuntime
) extends ComponentBase
    with FailFastCirceSupport {

  // Used by ensureToken
  private object tokenLock
  // There is a token retrieval in progress, just wait for it
  private var tokenInProgress: Option[Future[String]] = None
  // there is a token.
  private var token: Option[String] = None
  // token validity
  private var tokenValidUntil: Option[Instant] = None

  /** Returns a valid token, requesting a new one if necessary. */
  def getToken(): Future[String] = {
    tokenLock.synchronized {
      // Case 1: Is there a valid token already
      existingValidToken() match {
        case Some(token) =>
          return Future.successful(token)
        case None =>
          // Case 2: Is there a process looking for a token?
          tokenInProgress match {
            case Some(process) => return process
            case None          =>
              // ensure a new process
              val newProcess = updateTokenExecute()
              tokenInProgress = Some(newProcess)
              return newProcess
          }
      }
    }
  }

  private def existingValidToken(): Option[String] = {
    for {
      t <- token
      v <- tokenValidUntil
      if v.isAfter(clock.instant())
    } yield t
  }

  private def updateTokenExecute(): Future[String] = {
    val loginRequest = ApiLoginRequest(user, password.read(), MantikRegistryTokenProvider.Requester)
    val loginResponse = registryApi.login(loginRequest)

    loginResponse.onComplete {
      case Failure(ok) =>
        logger.info("Could not get a new token")
        tokenLock.synchronized {
          tokenInProgress = None
          token = None
          tokenValidUntil = None
        }
      case Success(value) =>
        tokenLock.synchronized {
          tokenInProgress = None
          token = Some(value.token)
          tokenValidUntil = value.validUntil
        }
    }

    loginResponse.map(_.token).recoverWith { case exception =>
      Future.failed(ErrorCodes.RemoteRegistryCouldNotGetToken.toException("Could not get token", exception))
    }
  }
}

object MantikRegistryTokenProvider {

  /** Request identification which is sent to the remote registry. */
  val Requester = s"MantikEngine ${BuildInfo.gitVersion}"
}

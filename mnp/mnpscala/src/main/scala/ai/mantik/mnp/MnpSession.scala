package ai.mantik.mnp

import ai.mantik.componently.rpc.StreamConversions
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.MnpService
import ai.mantik.mnp.protocol.mnp.{
  AboutSessionRequest,
  AboutSessionResponse,
  PushRequest,
  PushResponse,
  QuitSessionRequest,
  QuitSessionResponse
}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future

/**
  * Wraps an Mnp Session.
  * @param address address (for debugging and logging)
  * @param sessionId MNP session id
  */
class MnpSession(val address: String, val sessionId: String, mnpService: MnpService) {

  def mnpUrl: String = s"mnp://${address}/${sessionId}"

  def quit(): Future[QuitSessionResponse] = {
    mnpService.quitSession(QuitSessionRequest(sessionId))
  }

  def about(): Future[AboutSessionResponse] = {
    mnpService.aboutSession(
      AboutSessionRequest(
        sessionId
      )
    )
  }

  /** Gives access to task related operations. */
  def task(taskId: String): MnpTask = {
    new MnpTask(sessionId, taskId, mnpService)
  }
}

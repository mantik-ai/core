package ai.mantik.mnp

import ai.mantik.componently.rpc.StreamConversions
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.MnpService
import ai.mantik.mnp.protocol.mnp.{ AboutSessionRequest, AboutSessionResponse, PushRequest, PushResponse, QuitSessionRequest, QuitSessionResponse }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future

/** Wraps an Mnp Session. */
class MnpSession(sessionId: String, mnpService: MnpService) {

  def quit(): Future[QuitSessionResponse] = {
    mnpService.quitSession(QuitSessionRequest(sessionId))
  }

  def about(): Future[AboutSessionResponse] = {
    mnpService.aboutSession(AboutSessionRequest(
      sessionId
    ))
  }

  def runTask(taskId: String): RunTask = {
    new RunTask(sessionId, taskId, mnpService)
  }
}

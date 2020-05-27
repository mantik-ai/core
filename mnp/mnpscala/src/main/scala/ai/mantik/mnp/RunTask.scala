package ai.mantik.mnp

import ai.mantik.componently.rpc.{ RpcConversions, StreamConversions }
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.MnpService
import ai.mantik.mnp.protocol.mnp.{ PullRequest, PullResponse, PushRequest, PushResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future

class RunTask(sessionId: String, taskId: String, mnpService: MnpService) {

  def push(port: Int): Sink[ByteString, Future[PushResponse]] = {
    val (streamObserver, responseFuture) = StreamConversions.singleStreamObserverFuture[PushResponse]()
    val pusher = mnpService.push(streamObserver)

    val asSink = StreamConversions.sinkFromStreamObserverWithSpecialHandling[ByteString, PushRequest](
      pusher,
      first => {
        PushRequest(
          sessionId,
          taskId,
          port,
          data = RpcConversions.encodeByteString(first)
        )
      },
      next => {
        PushRequest(
          data = RpcConversions.encodeByteString(next)
        )
      },
      completer = { _ =>
        Some(
          PushRequest(
            done = true
          )
        )
      }
    ).mapMaterializedValue { _ => responseFuture }

    asSink
  }

  def pull(port: Int)(implicit mat: Materializer): Source[ByteString, _] = {
    val pullRequest = PullRequest(
      sessionId,
      taskId,
      port
    )
    val observer = StreamConversions.streamObserverSource[PullResponse]()
    val (streamObserver, source) = observer.preMaterialize()
    mnpService.pull(pullRequest, streamObserver)
    source.map { response =>
      RpcConversions.decodeByteString(response.data)
    }
  }
}

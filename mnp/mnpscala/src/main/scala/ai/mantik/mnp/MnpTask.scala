package ai.mantik.mnp

import ai.mantik.componently.rpc.{RpcConversions, StreamConversions}
import ai.mantik.mnp.protocol.mnp.MnpServiceGrpc.MnpService
import ai.mantik.mnp.protocol.mnp.{
  PullRequest,
  PullResponse,
  PushRequest,
  PushResponse,
  QueryTaskRequest,
  QueryTaskResponse
}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future, Promise}

class MnpTask(sessionId: String, taskId: String, mnpService: MnpService) {

  /**
    * Execute a push.
    * @return Sink with value of written bytes and Response.
    */
  def push(port: Int)(implicit ec: ExecutionContext): Sink[ByteString, Future[(Long, PushResponse)]] = {
    val (streamObserver, responseFuture) = StreamConversions.singleStreamObserverFuture[PushResponse]()
    val pusher = mnpService.push(streamObserver)

    val asSink = StreamConversions
      .sinkFromStreamObserverWithSpecialHandling[ByteString, PushRequest, Long](
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
        },
        initialState = 0L,
        stateUpdate = (bytes, request) => bytes + request.data.size()
      )
      .mapMaterializedValue { bytesFuture =>
        for {
          bytes <- bytesFuture
          response <- responseFuture
        } yield (bytes, response)
      }

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

  def query(ensure: Boolean): Future[QueryTaskResponse] = {
    val request = QueryTaskRequest(
      sessionId,
      taskId,
      ensure
    )
    mnpService.queryTask(request)
  }
}

package ai.mantik.planner.repository.rpc

import java.net.{ InetAddress, InetSocketAddress }

import ai.mantik.componently.rpc.{ RpcConversions, StreamConversions }
import ai.mantik.componently.{ AkkaRuntime, Component, ComponentBase }
import ai.mantik.planner.repository.FileRepository
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc.FileRepositoryService
import ai.mantik.planner.repository.protos.file_repository.{ DeleteFileRequest, LoadFileRequest, LoadFileResponse, RequestFileGetRequest, RequestFileStorageRequest, StoreFileRequest, StoreFileResponse }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.google.protobuf.empty.Empty
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class FileRepositoryClientImpl(service: FileRepositoryService)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with FileRepository {
  override def requestFileStorage(temporary: Boolean): Future[FileRepository.FileStorageResult] = {
    Conversions.decodeErrorsIn {
      service.requestFileStorage(RequestFileStorageRequest(temporary)).map { response =>
        FileRepository.FileStorageResult(
          response.fileId,
          response.path
        )
      }
    }
  }

  override def requestFileGet(id: String, optimistic: Boolean): Future[FileRepository.FileGetResult] = {
    Conversions.decodeErrorsIn {
      service.requestFileGet(RequestFileGetRequest(id, optimistic)).map { response =>
        FileRepository.FileGetResult(
          response.fileId,
          response.path,
          RpcConversions.decodeOptionalString(response.contentType)
        )
      }
    }
  }

  override def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Unit]]] = {
    // TODO: This should be simpler and should do real work at the moment the Sink is materialized.
    Conversions.decodeErrorsIn {
      Future {
        logger.debug(s"Requesting store content for ${id}")
        val firstRequest = StoreFileRequest(fileId = id, contentType = contentType)
        val (responseObserver, future) = StreamConversions.singleStreamObserverFuture[StoreFileResponse]()
        val inputHandler = service.storeFile(responseObserver)
        inputHandler.onNext(firstRequest)

        val sink = StreamConversions.sinkFromStreamObserver(inputHandler)
          .contramap { data: ByteString =>
            StoreFileRequest(chunk = RpcConversions.encodeByteString(data))
          }
        sink.mapMaterializedValue { _ =>
          future.map { _ =>
            logger.debug(s"Finished writing sink for ${id}")
            ()
          }
        }
      }
    }
  }

  override def deleteFile(id: String): Future[Boolean] = {
    Conversions.decodeErrorsIn {
      service.deleteFile(DeleteFileRequest(fileId = id)).map { response =>
        response.existed
      }
    }
  }

  override def loadFile(id: String): Future[Source[ByteString, _]] = {
    Conversions.decodeErrorsIn {
      Future {
        val source = StreamConversions.streamObserverSource[LoadFileResponse]()
        val adapted = source.mapMaterializedValue { streamObserver =>
          service.loadFile(LoadFileRequest(fileId = id), streamObserver)
        }.map { response =>
          RpcConversions.decodeByteString(response.chunk)
        }.mapError(Conversions.decodeErrors)
        adapted
      }
    }
  }

  override def address(): InetSocketAddress = {
    val response = Await.result(
      Conversions.decodeErrorsIn {
        service.address(Empty())
      }, 60.seconds
    )
    val addr = InetAddress.getByName(response.host)
    new InetSocketAddress(addr, response.port)
  }
}

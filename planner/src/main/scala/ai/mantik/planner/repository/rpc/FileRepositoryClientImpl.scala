package ai.mantik.planner.repository.rpc

import java.net.{ InetAddress, InetSocketAddress }

import ai.mantik.componently.rpc.{ RpcConversions, StreamConversions }
import ai.mantik.componently.{ AkkaRuntime, Component, ComponentBase }
import ai.mantik.planner.repository.FileRepository
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc.FileRepositoryService
import ai.mantik.planner.repository.protos.file_repository.{ CopyFileRequest, DeleteFileRequest, LoadFileRequest, LoadFileResponse, RequestFileGetRequest, RequestFileStorageRequest, StoreFileRequest, StoreFileResponse }
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.util.ByteString
import com.google.protobuf.empty.Empty
import com.typesafe.scalalogging.Logger
import io.grpc.stub.{ StreamObserver, StreamObservers }
import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

@Singleton
class FileRepositoryClientImpl @Inject() (service: FileRepositoryService)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with FileRepository {
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
          response.isTemporary,
          response.path,
          RpcConversions.decodeOptionalString(response.contentType)
        )
      }
    }
  }

  override def storeFile(id: String, contentType: String): Future[Sink[ByteString, Future[Long]]] = {
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
          future.map { response =>
            logger.debug(s"Finished writing sink for ${id}")
            response.bytes
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

  override def loadFile(id: String): Future[(String, Source[ByteString, _])] = {
    val (observer, result) = StreamConversions.headerStreamSource[LoadFileResponse, String, ByteString](
      decodeHeader = x => x.contentType,
      decodeAll = x => RpcConversions.decodeByteString(x.chunk),
      errorDecoder = Conversions.decodeErrors
    )
    service.loadFile(LoadFileRequest(fileId = id), observer)
    result
  }

  override def copy(from: String, to: String): Future[Unit] = {
    Conversions.decodeErrorsIn {
      service.copyFile(
        CopyFileRequest(fromId = from, toId = to)
      ).map { _ => () }
    }
  }
}

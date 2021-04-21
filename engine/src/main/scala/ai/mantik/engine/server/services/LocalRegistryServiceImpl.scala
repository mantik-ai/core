package ai.mantik.engine.server.services

import ai.mantik.componently.rpc.{RpcConversions, StreamConversions}
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.errors.InvalidMantikHeaderException
import ai.mantik.elements.{ItemId, MantikId, MantikHeader, NamedMantikId}
import ai.mantik.engine.protos.local_registry.LocalRegistryServiceGrpc.LocalRegistryService
import ai.mantik.engine.protos.local_registry._
import ai.mantik.planner.repository.MantikRegistry.PayloadSource
import ai.mantik.planner.repository.{LocalMantikRegistry, MantikArtifact}
import io.grpc.stub.StreamObserver
import javax.inject.Inject

import scala.concurrent.Future
import scala.util.{Failure, Success}

class LocalRegistryServiceImpl @Inject() (localMantikRegistry: LocalMantikRegistry)(implicit akkaRuntime: AkkaRuntime)
    extends ComponentBase
    with LocalRegistryService
    with RpcServiceBase {

  override def getArtifact(request: GetArtifactRequest): Future[GetArtifactResponse] = {
    handleErrors {
      val mantikId = MantikId.fromString(request.mantikId)
      localMantikRegistry.get(mantikId).map { artifact =>
        GetArtifactResponse(
          artifact = Some(Converters.encodeMantikArtifact(artifact))
        )
      }
    }
  }

  override def tagArtifact(request: TagArtifactRequest): Future[TagArtifactResponse] = {
    handleErrors {
      val mantikId = MantikId.fromString(request.mantikId)
      val newNamedMantikId = NamedMantikId.fromString(request.newNamedMantikId)
      for {
        item <- localMantikRegistry.get(mantikId)
        updated <- localMantikRegistry.ensureMantikId(item.itemId, newNamedMantikId)
      } yield TagArtifactResponse(
        changed = updated
      )
    }
  }

  override def listArtifacts(request: ListArtifactsRequest): Future[ListArtifactResponse] = {
    handleErrors {
      localMantikRegistry
        .list(
          alsoAnonymous = request.anonymous,
          deployedOnly = request.deployed,
          kindFilter = RpcConversions.decodeOptionalString(request.kind)
        )
        .map { items =>
          ListArtifactResponse(
            items.map(Converters.encodeMantikArtifact)
          )
        }
    }
  }

  override def addArtifact(
      responseObserver: StreamObserver[AddArtifactResponse]
  ): StreamObserver[AddArtifactRequest] = {
    StreamConversions.respondMultiInSingleOutWithHeader[AddArtifactRequest, AddArtifactResponse](
      translateError,
      responseObserver
    ) { case (header, source) =>
      val namedId = RpcConversions
        .decodeOptionalString(header.namedMantikId)
        .map(
          NamedMantikId.apply
        )
      val itemId = ItemId.generate()
      val maybeContentType = RpcConversions.decodeOptionalString(header.contentType)
      val mantikArtifact = MantikArtifact(
        mantikHeader = header.mantikHeader,
        fileId = None, // will be set by response
        namedId = namedId,
        itemId = itemId
      )
      mantikArtifact.parsedMantikHeader // force parsing
      val maybePayloadSource: Option[PayloadSource] = maybeContentType.map { contentType =>
        val decodedSource = source.map(r => RpcConversions.decodeByteString(r.payload))
        contentType -> decodedSource
      }
      logger.info(s"Adding artifact ${mantikArtifact.mantikId} (payload=${maybeContentType})...")
      localMantikRegistry
        .addMantikArtifact(
          mantikArtifact,
          maybePayloadSource
        )
        .map { response =>
          AddArtifactResponse(
            Some(Converters.encodeMantikArtifact(response))
          )
        }
    }
  }

  override def getArtifactWithPayload(
      request: GetArtifactRequest,
      responseObserver: StreamObserver[GetArtifactWithPayloadResponse]
  ): Unit = {
    val mantikId = MantikId.decodeString(request.mantikId) match {
      case Left(failure) =>
        responseObserver.onError(encodeErrorIfPossible(failure))
        return
      case Right(mantikId) =>
        mantikId
    }
    localMantikRegistry.get(mantikId).onComplete {
      case Success(artifact) =>
        val converted = Converters.encodeMantikArtifact(artifact)
        artifact.fileId match {
          case None =>
            // there is no payload
            responseObserver.onNext(
              GetArtifactWithPayloadResponse(artifact = Some(converted))
            )
            responseObserver.onCompleted()
          case Some(fileId) =>
            // there is payload
            localMantikRegistry.getPayload(fileId).onComplete {
              case Failure(error) =>
                responseObserver.onError(encodeErrorIfPossible(error))
              case Success((contentType, source)) =>
                val header = GetArtifactWithPayloadResponse(
                  artifact = Some(converted),
                  contentType = contentType
                )
                responseObserver.onNext(header)
                val adaptedSource = source.map { bytes =>
                  GetArtifactWithPayloadResponse(payload = RpcConversions.encodeByteString(bytes))
                }
                StreamConversions.pumpSourceIntoStreamObserver(adaptedSource, responseObserver)
            }
        }
      case Failure(error) =>
        responseObserver.onError(encodeErrorIfPossible(error))
    }
  }
}

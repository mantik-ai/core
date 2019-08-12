package ai.mantik.planner.repository.rpc

import ai.mantik.componently.{ AkkaRuntime, Component, ComponentBase }
import ai.mantik.planner.repository.{ Errors, Repository }
import ai.mantik.planner.repository.protos.repository.{ EnsureMantikIdRequest, EnsureMantikIdResponse, GetItemRequest, GetItemResponse, RemoveRequest, RemoveResponse, SetDeploymentInfoRequest, SetDeploymentInfoResponse, StoreRequest, StoreResponse }
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryService
import javax.inject.Inject

import scala.concurrent.Future

class RepositoryServiceImpl @Inject() (repository: Repository)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with RepositoryService {
  override def get(request: GetItemRequest): Future[GetItemResponse] = {
    errorHandling {
      val mantikId = Conversions.decodeMantikId(request.mantikId)
      repository.get(mantikId).map { response =>
        GetItemResponse(
          Some(Conversions.encodeMantikArtifact(response))
        )
      }
    }
  }

  override def store(request: StoreRequest): Future[StoreResponse] = {
    errorHandling {
      val decoded = Conversions.decodeMantikArtifact(request.artifact.getOrElse(
        throw new RuntimeException("Missing Artifact")
      ))
      repository.store(decoded).map { _ =>
        StoreResponse()
      }
    }
  }

  override def ensureMantikId(request: EnsureMantikIdRequest): Future[EnsureMantikIdResponse] = {
    errorHandling {
      val itemId = Conversions.decodeItemId(request.itemId)
      val mantikId = Conversions.decodeMantikId(request.mantikId)
      repository.ensureMantikId(itemId, mantikId).map { changed =>
        EnsureMantikIdResponse(changed = changed)
      }
    }
  }

  override def setDeploymentInfo(request: SetDeploymentInfoRequest): Future[SetDeploymentInfoResponse] = {
    errorHandling {
      val itemId = Conversions.decodeItemId(request.itemId)
      val deploymentInfo = request.info.map(Conversions.decodeDeploymentInfo)
      repository.setDeploymentInfo(itemId, deploymentInfo).map { updated =>
        SetDeploymentInfoResponse(updated)
      }
    }
  }

  override def remove(request: RemoveRequest): Future[RemoveResponse] = {
    errorHandling {
      val mantikid = Conversions.decodeMantikId(request.mantikId)
      repository.remove(mantikid).map { found =>
        RemoveResponse(found)
      }
    }
  }

  private def errorHandling[T](f: => Future[T]): Future[T] = {
    Conversions.encodeErrorsIn(f)
  }
}

package ai.mantik.planner.repository.rpc

import ai.mantik.componently.{ AkkaRuntime, Component, ComponentBase }
import ai.mantik.planner.repository.{ Errors, Repository }
import ai.mantik.planner.repository.protos.repository.{ GetItemRequest, GetItemResponse, GetItemWithHullResponse, RemoveRequest, RemoveResponse, SetDeploymentInfoRequest, SetDeploymentInfoResponse, StoreRequest, StoreResponse }
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryService

import scala.concurrent.Future

class RepositoryServiceImpl(repository: Repository)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with RepositoryService {
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

  override def getWithHull(request: GetItemRequest): Future[GetItemWithHullResponse] = {
    errorHandling {
      val mantikId = Conversions.decodeMantikId(request.mantikId)
      repository.getWithHull(mantikId).map { response =>
        GetItemWithHullResponse(
          Some(Conversions.encodeMantikArtifact(response._1)),
          response._2.map(Conversions.encodeMantikArtifact)
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

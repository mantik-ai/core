package ai.mantik.planner.repository.rpc

import ai.mantik.elements.{ ItemId, MantikId }
import ai.mantik.planner.repository.protos.repository.{ GetItemRequest, RemoveRequest, SetDeploymentInfoRequest, StoreRequest }
import ai.mantik.planner.repository.{ DeploymentInfo, Errors, MantikArtifact, Repository }
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryService
import ai.mantik.planner.utils.{ AkkaRuntime, Component }
import io.grpc.Status.Code
import io.grpc.{ Status, StatusRuntimeException }

import scala.concurrent.Future

class RepositoryClientImpl(service: RepositoryService)(implicit val akkaRuntime: AkkaRuntime) extends Repository with Component {
  override def get(id: MantikId): Future[MantikArtifact] = {
    decodeErrors {
      service.get(GetItemRequest(Conversions.encodeMantikId(id))).map { response =>
        val force = response.artifact.getOrElse {
          throw new RuntimeException("Missing artifact?!")
        }
        Conversions.decodeMantikArtifact(force)
      }
    }
  }

  override def store(mantikArtefact: MantikArtifact): Future[Unit] = {
    decodeErrors {
      service.store(StoreRequest(Some(Conversions.encodeMantikArtifact(mantikArtefact)))).map { _ =>
        ()
      }
    }
  }

  override def setDeploymentInfo(itemId: ItemId, state: Option[DeploymentInfo]): Future[Boolean] = {
    decodeErrors {
      service.setDeploymentInfo(
        SetDeploymentInfoRequest(
          Conversions.encodeItemId(itemId),
          state.map(Conversions.encodeDeploymentInfo)
        )
      ).map { response =>
          response.updated
        }
    }
  }

  override def remove(id: MantikId): Future[Boolean] = {
    decodeErrors {
      service.remove(RemoveRequest(Conversions.encodeMantikId(id))).map { response =>
        response.found
      }
    }
  }

  private def decodeErrors[T](f: => Future[T]): Future[T] = {
    Conversions.decodeErrorsIn(f)
  }
}

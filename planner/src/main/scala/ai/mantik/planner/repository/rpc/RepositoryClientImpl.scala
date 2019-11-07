package ai.mantik.planner.repository.rpc

import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.componently.{ AkkaRuntime, Component, ComponentBase }
import ai.mantik.elements.{ ItemId, MantikId, NamedMantikId }
import ai.mantik.planner.repository.protos.repository.{ EnsureMantikIdRequest, GetItemRequest, ListRequest, RemoveRequest, SetDeploymentInfoRequest, StoreRequest }
import ai.mantik.planner.repository.{ DeploymentInfo, MantikArtifact, Repository }
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryService
import io.grpc.Status.Code
import io.grpc.{ Status, StatusRuntimeException }
import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

@Singleton
class RepositoryClientImpl @Inject() (service: RepositoryService)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with Repository {
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

  override def ensureMantikId(id: ItemId, newName: NamedMantikId): Future[Boolean] = {
    decodeErrors {
      service.ensureMantikId(EnsureMantikIdRequest(
        itemId = id.toString,
        mantikId = newName.toString
      )).map { response =>
        response.changed
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

  override def list(alsoAnonymous: Boolean, deployedOnly: Boolean, kindFilter: Option[String]): Future[IndexedSeq[MantikArtifact]] = {
    decodeErrors {
      service.list(
        ListRequest(
          alsoAnonymous = alsoAnonymous,
          deployedOnly = deployedOnly,
          kindFilter = RpcConversions.encodeOptionalString(kindFilter)
        )
      ).map { response =>
          response.artifacts.map {
            Conversions.decodeMantikArtifact
          }.toIndexedSeq
        }
    }
  }
}

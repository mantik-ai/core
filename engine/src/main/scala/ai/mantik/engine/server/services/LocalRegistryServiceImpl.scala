package ai.mantik.engine.server.services

import ai.mantik.componently.rpc.RpcConversions
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.MantikId
import ai.mantik.engine.protos.local_registry.{ GetArtifactRequest, GetArtifactResponse, ListArtifactResponse, ListArtifactsRequest }
import ai.mantik.engine.protos.local_registry.LocalRegistryServiceGrpc.LocalRegistryService
import ai.mantik.planner.repository.LocalMantikRegistry
import javax.inject.Inject

import scala.concurrent.Future

class LocalRegistryServiceImpl @Inject() (localMantikRegistry: LocalMantikRegistry)(implicit akkaRuntime: AkkaRuntime)
  extends ComponentBase with LocalRegistryService with RpcServiceBase {

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

  override def listArtifacts(request: ListArtifactsRequest): Future[ListArtifactResponse] = {
    handleErrors {
      localMantikRegistry.list(
        alsoAnonymous = request.anonymous,
        deployedOnly = request.deployed,
        kindFilter = RpcConversions.decodeOptionalString(request.kind)
      ).map { items =>
          ListArtifactResponse(
            items.map(Converters.encodeMantikArtifact)
          )
        }
    }
  }
}

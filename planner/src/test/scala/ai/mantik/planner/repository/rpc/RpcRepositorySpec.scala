package ai.mantik.planner.repository.rpc

import ai.mantik.planner.repository.impl.{ LocalRepository, RepositorySpecBase }
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryServiceStub
import ai.mantik.testutils.TempDirSupport

class RpcRepositorySpec extends RepositorySpecBase with TempDirSupport {

  override type RepoType = RepositoryClientImpl

  override protected def createRepo(): RepositoryClientImpl = {
    val runtimeOverride = akkaRuntime.withConfigOverrides(
      "mantik.repository.artifactRepository.local.directory" -> tempDirectory.toString
    )
    val backend = new LocalRepository()(runtimeOverride)
    val toRpcWrapper = new RepositoryServiceImpl(backend)

    val rpcTest = new RpcTestConnection(
      RepositoryServiceGrpc.bindService(toRpcWrapper, ec)
    )

    val repositoryPureClient = new RepositoryServiceStub(rpcTest.channel)

    val backWrapper = new RepositoryClientImpl(repositoryPureClient) {
      override def shutdown(): Unit = {
        super.shutdown()
        rpcTest.shutdown()
        backend.shutdown()
      }
    }
    backWrapper
  }
}

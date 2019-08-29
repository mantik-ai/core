package ai.mantik.planner.repository.rpc

import ai.mantik.planner.repository.impl.{ LocalRepository, RepositorySpecBase }
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc
import ai.mantik.planner.repository.protos.repository.RepositoryServiceGrpc.RepositoryServiceStub
import ai.mantik.testutils.TempDirSupport

import scala.concurrent.Future

class RpcRepositorySpec extends RepositorySpecBase with TempDirSupport {

  override type RepoType = RepositoryClientImpl

  override protected def createRepo(): RepositoryClientImpl = {
    val backend = new LocalRepository(tempDirectory)
    val toRpcWrapper = new RepositoryServiceImpl(backend)

    val rpcTest = new RpcTestConnection(
      RepositoryServiceGrpc.bindService(toRpcWrapper, ec)
    )

    val repositoryPureClient = new RepositoryServiceStub(rpcTest.channel)

    val backWrapper = new RepositoryClientImpl(repositoryPureClient)
    akkaRuntime.lifecycle.addShutdownHook {
      rpcTest.shutdown()
      Future.successful(())
    }
    backWrapper
  }
}

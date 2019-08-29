package ai.mantik.planner.repository.rpc

import ai.mantik.planner.repository.impl.{ FileRepositorySpecBase, LocalFileRepository, NonAsyncFileRepository }
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc
import ai.mantik.planner.repository.protos.file_repository.FileRepositoryServiceGrpc.FileRepositoryServiceStub
import ai.mantik.testutils.TempDirSupport

class RpcFileRepositorySpec extends FileRepositorySpecBase with TempDirSupport {
  override type RepoType = FileRepositoryClientImpl with NonAsyncFileRepository

  override protected def createRepo(): RepoType = {
    val fileService = new LocalFileRepository(tempDirectory)
    val fileServer = new FileRepositoryServiceImpl(fileService)

    val rpcTestConnection = new RpcTestConnection(FileRepositoryServiceGrpc.bindService(fileServer, ec))
    val client = new FileRepositoryServiceStub(rpcTestConnection.channel)
    val backWrapper = new FileRepositoryClientImpl(client) with NonAsyncFileRepository
    backWrapper
  }
}

package ai.mantik.engine.server.services

import java.io.File

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.NamedMantikId
import ai.mantik.engine.protos.debug.{ AddLocalMantikDirectoryRequest, AddLocalMantikDirectoryResponse }
import ai.mantik.engine.protos.debug.DebugServiceGrpc.DebugService
import ai.mantik.planner.Context
import javax.inject.Inject

import scala.concurrent.Future

class DebugServiceImpl @Inject() (context: Context)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with DebugService with RpcServiceBase {

  override def addLocalMantikDirectory(request: AddLocalMantikDirectoryRequest): Future[AddLocalMantikDirectoryResponse] = handleErrors {
    val mantikId = if (request.name.isEmpty) {
      None
    } else {
      Some(NamedMantikId.fromString(request.name))
    }
    val idToUse = context.pushLocalMantikFile(
      new File(request.directory).toPath,
      id = mantikId
    )
    Future.successful(
      AddLocalMantikDirectoryResponse(
        idToUse.toString
      )
    )
  }
}

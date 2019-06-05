package ai.mantik.engine.server.services

import java.io.File

import ai.mantik.engine.protos.debug.{ AddLocalMantikDirectoryRequest, AddLocalMantikDirectoryResponse }
import ai.mantik.engine.protos.debug.DebugServiceGrpc.DebugService
import ai.mantik.planner.Context
import ai.mantik.repository.MantikId

import scala.concurrent.Future

class DebugServiceImpl(context: Context) extends DebugService {

  override def addLocalMantikDirectory(request: AddLocalMantikDirectoryRequest): Future[AddLocalMantikDirectoryResponse] = {
    val mantikId = if (request.name.isEmpty) {
      None
    } else {
      Some(MantikId.fromString(request.name))
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

package ai.mantik.engine.server.services

import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.engine.VersionResponse
import com.google.protobuf.empty.Empty
import javax.inject.Inject

import scala.concurrent.Future

class AboutServiceImpl @Inject() extends AboutService {

  override def version(request: Empty): Future[VersionResponse] = {
    val version = s"${BuildInfo.version} (git: ${BuildInfo.gitVersion}  build:${BuildInfo.buildNum})"
    val response = VersionResponse(
      version
    )
    Future.successful(response)
  }
}

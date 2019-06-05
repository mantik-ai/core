package ai.mantik.engine.server.services

import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.engine.VersionResponse
import com.google.protobuf.empty.Empty

import scala.concurrent.Future

class AboutServiceImpl extends AboutService {

  override def version(request: Empty): Future[VersionResponse] = {
    val version = s"${BuildInfo.version} (git: ${BuildInfo.gitVersion}  build:${BuildInfo.buildNum})"
    val response = VersionResponse(
      version
    )
    Future.successful(response)
  }
}

package ai.mantik.engine.server.services

import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.engine.{ ClientConfigResponse, VersionResponse }
import ai.mantik.planner.ClientConfig
import com.google.protobuf.empty.Empty
import javax.inject.{ Inject, Named }
import io.circe.syntax._

import scala.concurrent.Future

class AboutServiceImpl @Inject() (clientConfig: ClientConfig) extends AboutService {

  override def version(request: Empty): Future[VersionResponse] = {
    val version = s"${BuildInfo.version} (git: ${BuildInfo.gitVersion}  build:${BuildInfo.buildNum})"
    val response = VersionResponse(
      version
    )
    Future.successful(response)
  }

  override def clientConfig(request: Empty): Future[ClientConfigResponse] = {
    Future.successful {
      ClientConfigResponse(
        config = clientConfig.asJson.toString()
      )
    }
  }
}

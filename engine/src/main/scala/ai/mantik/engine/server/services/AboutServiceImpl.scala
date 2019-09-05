package ai.mantik.engine.server.services

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.engine.buildinfo.BuildInfo
import ai.mantik.engine.protos.engine.AboutServiceGrpc.AboutService
import ai.mantik.engine.protos.engine.{ ClientConfigResponse, VersionResponse }
import ai.mantik.planner.ClientConfig
import com.google.protobuf.empty.Empty
import com.typesafe.scalalogging.Logger
import javax.inject.{ Inject, Named }
import io.circe.syntax._

import scala.concurrent.Future

class AboutServiceImpl @Inject() (clientConfig: ClientConfig)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with AboutService with RpcServiceBase {

  override def version(request: Empty): Future[VersionResponse] = handleErrors {
    val version = s"${BuildInfo.version} (git: ${BuildInfo.gitVersion}  build:${BuildInfo.buildNum})"
    val response = VersionResponse(
      version
    )
    Future.successful(response)
  }

  override def clientConfig(request: Empty): Future[ClientConfigResponse] = handleErrors {
    Future.successful {
      ClientConfigResponse(
        config = clientConfig.asJson.toString()
      )
    }
  }
}

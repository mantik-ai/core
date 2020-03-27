package ai.mantik.planner.impl.exec

import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.executor.Executor
import ai.mantik.executor.model.PublishServiceRequest
import ai.mantik.planner.repository.FileRepositoryServer
import io.circe.syntax._
import akka.http.scaladsl.model.Uri
import javax.inject.{ Inject, Singleton }

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Helper, which maps the FileRepositoryServer inside the Executor.
 * This is done by starting a remote service.
 * It is necessary, so that Jobs/Services inside the Executor can talk
 * to mantik headers.
 */
@Singleton
private[planner] class FileRepositoryServerRemotePresence @Inject() (
    fileRepositoryServer: FileRepositoryServer,
    executor: Executor
)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase {

  private val kubernetesName = config.getString("mantik.core.fileServiceKubernetesName")
  private val isolationSpace = config.getString("mantik.planner.isolationSpace")
  private val serverAddress = fileRepositoryServer.address()
  /*
  If the File Service is immediately used after publishing, connections seem to time out.
  This seems to be like this bug: https://github.com/kubernetes/kubernetes/issues/48719
   */
  private val sleepAfterPublish = 3.seconds

  def assembledRemoteUri(): Uri = {
    Await.result(prepareKubernetesFileServiceResult, 60.seconds)
  }

  private lazy val prepareKubernetesFileServiceResult = FutureHelper.time(logger, "Prepare File Service") {
    val request = PublishServiceRequest(
      isolationSpace = isolationSpace,
      serviceName = kubernetesName,
      port = serverAddress.port,
      externalName = serverAddress.host,
      externalPort = serverAddress.port
    )
    logger.debug(s"Preparing FileService ${request.asJson}")
    executor.publishService(request).map { response =>
      logger.info(s"FileService is published with ${response.name}")
      logger.info(s"Will sleep for ${sleepAfterPublish} to avoid timeouted TCP Connections")
      Thread.sleep(sleepAfterPublish.toMillis)
      Uri(s"http://${response.name}") // is of format domain:port
    }
  }
}

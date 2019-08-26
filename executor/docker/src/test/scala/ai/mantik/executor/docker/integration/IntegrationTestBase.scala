package ai.mantik.executor.docker.integration

import java.nio.file.{ Files, Path }

import ai.mantik.componently.AkkaRuntime
import ai.mantik.executor.Executor
import ai.mantik.executor.common.test.integration.IntegrationBase
import ai.mantik.executor.docker.{ DockerConstants, DockerExecutor, DockerExecutorConfig }
import ai.mantik.executor.docker.api.DockerClient
import ai.mantik.testutils.{ AkkaSupport, TempDirSupport, TestBase }
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.scalatest.time.{ Millis, Span }

import scala.concurrent.duration._

abstract class IntegrationTestBase extends TestBase with AkkaSupport with TempDirSupport with IntegrationBase {
  implicit def akkaRuntime: AkkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)

  protected lazy val dockerClient: DockerClient = new DockerClient()

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    killOldMantikContainers()
  }

  private def killOldMantikContainers(): Unit = {
    val containers = await(dockerClient.listContainers((true)))
    val mantikContainers = containers.filter(
      _.Labels.get(DockerConstants.ManagedByLabelName).contains(DockerConstants.ManabedByLabelValue)
    )
    if (mantikContainers.isEmpty) {
      logger.info("No old mantik containers to kill")
    }
    mantikContainers.foreach { container =>
      logger.info(s"Killing Container ${container.Names}/${container.Id}")
      await(dockerClient.removeContainer(container.Id, true))
    }
    val volumes = await(dockerClient.listVolumes(()))
    val mantikVolumes = volumes.Volumes.filter(
      _.effectiveLabels.get(DockerConstants.ManabedByLabelValue).contains(DockerConstants.ManagedByLabelName)
    )
    if (mantikVolumes.isEmpty) {
      logger.info("No old mantik volumes to kill")
    }
    mantikVolumes.foreach { volume =>
      logger.info(s"Killing Volume ${volume.Name}")
      await(dockerClient.removeVolume(volume.Name))
    }
  }

  override protected def afterAll(): Unit = {
    dockerClient.shutdown()
    super.afterAll()
  }

  override protected lazy val typesafeConfig: Config = {
    ConfigFactory.load("systemtest.conf")
  }

  override def withExecutor[T](f: Executor => T): Unit = {
    val config = DockerExecutorConfig.fromTypesafeConfig(typesafeConfig)
    val executor = new DockerExecutor(dockerClient, config)
    try {
      f(executor)
    } finally {
      executor.shutdown()
    }
  }
}

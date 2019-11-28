package ai.mantik.planner.integration

import ai.mantik.componently.AkkaRuntime
import ai.mantik.componently.utils.GlobalLocalAkkaRuntime
import ai.mantik.executor.Executor
import ai.mantik.executor.client.ExecutorClient
import ai.mantik.executor.kubernetes.{ ExecutorForIntegrationTests, KubernetesCleaner }
import ai.mantik.planner.Context
import ai.mantik.planner.impl.ContextImpl
import ai.mantik.planner.repository.{ FileRepository, FileRepositoryServer, RemoteMantikRegistry }
import ai.mantik.planner.repository.impl.{ TempFileRepository, TempRepository }
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.time.{ Millis, Span }

import scala.concurrent.duration._

/** Base class for integration tests having a full running executor instance. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport with GlobalLocalAkkaRuntime {

  protected var embeddedExecutor: ExecutorForIntegrationTests = _
  override protected lazy val typesafeConfig: Config = ConfigFactory.load("systemtest.conf")
  private var _context: Context = _
  private var _fileRepo: FileRepository = _

  implicit def context: Context = _context

  protected def fileRepository: FileRepository = _fileRepo

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    embeddedExecutor = new ExecutorForIntegrationTests(typesafeConfig)
    scrapKubernetes()

    val repository = new TempRepository()
    _fileRepo = new TempFileRepository()
    val fileRepoServer = new FileRepositoryServer(_fileRepo)
    val executor = constructExecutorClient()
    val registry = RemoteMantikRegistry.empty

    _context = ContextImpl.constructWithComponents(
      repository, _fileRepo, fileRepoServer, executor, registry
    )
  }

  private def constructExecutorClient()(implicit akkaRuntime: AkkaRuntime): Executor = {
    val executorUrl = akkaRuntime.config.getString("mantik.executor.client.executorUrl")
    val executor: Executor = new ExecutorClient(executorUrl)
    executor
  }

  private def scrapKubernetes(): Unit = {
    val cleaner = new KubernetesCleaner(embeddedExecutor.kubernetesClient, embeddedExecutor.executorConfig)
    cleaner.deleteKubernetesContent()
  }

  override protected def afterAll(): Unit = {
    embeddedExecutor.stop()
    super.afterAll()
  }
}

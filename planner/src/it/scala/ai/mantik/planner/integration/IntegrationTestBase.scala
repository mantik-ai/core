package ai.mantik.planner.integration

import ai.mantik.componently.utils.GlobalLocalAkkaRuntime
import ai.mantik.elements.errors.ConfigurationException
import ai.mantik.executor.{ExecutorFileStorage, ExecutorForIntegrationTest}
import ai.mantik.executor.docker.DockerExecutorForIntegrationTest
import ai.mantik.executor.kubernetes.KubernetesExecutorForIntegrationTests
import ai.mantik.executor.s3storage.S3Storage
import ai.mantik.planner.PlanningContext
import ai.mantik.planner.impl.PlanningContextImpl
import ai.mantik.planner.impl.exec.{
  ExecutionPayloadProvider,
  ExecutorStorageExecutionPayloadProvider,
  LocalServerExecutionPayloadProvider
}
import ai.mantik.planner.repository.impl.{TempFileRepository, TempRepository}
import ai.mantik.planner.repository.{
  FileRepository,
  FileRepositoryServer,
  MantikArtifactRetriever,
  RemoteMantikRegistry,
  Repository
}
import ai.mantik.testutils.{AkkaSupport, TestBase}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.time.{Millis, Span}

import scala.concurrent.duration._

/** Base class for integration tests having a full running executor instance. */
abstract class IntegrationTestBase extends TestBase with AkkaSupport with GlobalLocalAkkaRuntime {

  protected var embeddedExecutor: ExecutorForIntegrationTest = _
  override protected lazy val typesafeConfig: Config = ConfigFactory.load("systemtest.conf")
  private var _context: PlanningContextImpl = _
  private var _fileRepo: FileRepository = _

  implicit def context: PlanningContext = _context

  protected def fileRepository: FileRepository = _fileRepo

  protected def retriever: MantikArtifactRetriever = _context.retriever

  override protected val timeout: FiniteDuration = 30.seconds

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(30000, Millis)),
    interval = scaled(Span(500, Millis))
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    embeddedExecutor = makeExecutorForIntegrationTest()
    embeddedExecutor.scrap()
    embeddedExecutor.start()

    val repository = new TempRepository()
    _fileRepo = new TempFileRepository()
    val registry = RemoteMantikRegistry.empty
    val payloadProvider = makeExecutionPayloadProviderForIntegrationTest(_fileRepo, repository)

    _context = PlanningContextImpl.constructWithComponents(
      repository,
      _fileRepo,
      embeddedExecutor.executor,
      registry,
      payloadProvider
    )
  }

  private def makeExecutorForIntegrationTest(): ExecutorForIntegrationTest = {
    val executorType = typesafeConfig.getString("mantik.executor.type")
    executorType match {
      case "docker"     => return new DockerExecutorForIntegrationTest(typesafeConfig)
      case "kubernetes" => return new KubernetesExecutorForIntegrationTests(typesafeConfig)
      case _            => throw new ConfigurationException("Bad executor type")
    }
  }

  private def makeExecutionPayloadProviderForIntegrationTest(
      fileRepository: FileRepository,
      repo: Repository
  ): ExecutionPayloadProvider = {
    val executorType = typesafeConfig.getString("mantik.executor.type")
    executorType match {
      case "docker" =>
        new LocalServerExecutionPayloadProvider(fileRepository, repo)
      case "kubernetes" =>
        val storage = new S3Storage()
        await(storage.deleteAllManaged())
        new ExecutorStorageExecutionPayloadProvider(fileRepository, repo, storage)
      case _ => throw new ConfigurationException("Bad executor type")
    }
  }

  override protected def afterAll(): Unit = {
    embeddedExecutor.stop()
    super.afterAll()
  }
}

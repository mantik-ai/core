package ai.mantik.planner.impl

import java.nio.file.Path

import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.elements.{ MantikId, NamedMantikId }
import ai.mantik.executor.Executor
import ai.mantik.executor.client.ExecutorClient
import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import ai.mantik.planner.impl.exec.{ FileRepositoryServerRemotePresence, PlanExecutorImpl }
import ai.mantik.planner.repository.impl.{ LocalMantikRegistryImpl, MantikArtifactRetrieverImpl, TempFileRepository, TempRepository }
import ai.mantik.planner.repository.{ Errors, FileRepository, FileRepositoryServer, LocalMantikRegistry, MantikArtifactRetriever, RemoteMantikRegistry, Repository }
import javax.inject.Inject

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.reflect.ClassTag

private[planner] class ContextImpl @Inject() (
    val localRegistry: LocalMantikRegistry,
    val planner: Planner,
    val planExecutor: PlanExecutor,
    val remoteRegistry: RemoteMantikRegistry,
    val retriever: MantikArtifactRetriever
)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with Context {
  private val jobTimeout = config.getFiniteDuration("mantik.planner.jobTimeout")

  override def loadDataSet(id: MantikId): DataSet = {
    load[DataSet](id)
  }

  override def loadAlgorithm(id: MantikId): Algorithm = {
    load[Algorithm](id)
  }

  override def loadTrainableAlgorithm(id: MantikId): TrainableAlgorithm = {
    load[TrainableAlgorithm](id)
  }

  override def loadPipeline(id: MantikId): Pipeline = {
    load[Pipeline](id)
  }

  override def pull(id: MantikId): MantikItem = {
    val (artifact, hull) = await(retriever.pull(id))
    MantikItem.fromMantikArtifact(artifact, hull)
  }

  private def load[T <: MantikItem](id: MantikId)(implicit classTag: ClassTag[T#DefinitionType]): T = {
    val (artifact, hull) = await(retriever.get(id))
    artifact.mantikfile.definitionAs[T#DefinitionType] match {
      case Left(error) => throw new Errors.WrongTypeException("Wrong item type", error)
      case _           => // ok
    }
    val item = MantikItem.fromMantikArtifact(artifact, hull)
    item.asInstanceOf[T]
  }

  override def execute[T](action: Action[T]): T = {
    val plan = planner.convert(action)
    val result = await(planExecutor.execute(plan), jobTimeout)
    result
  }

  private def await[T](future: Future[T], timeout: Duration = Duration.Inf) = {
    Await.result(future, timeout)
  }

  override def pushLocalMantikFile(dir: Path, id: Option[NamedMantikId] = None): MantikId = {
    await(retriever.addLocalDirectoryToRepository(dir, id)).mantikId
  }
}

private[mantik] object ContextImpl {

  /** Construct a Client only context (for integration tests.). */
  def constructTempClient()(implicit akkaRuntime: AkkaRuntime): Context = {
    val repository = new TempRepository()
    val fileRepo = new TempFileRepository()
    val fileRepoServer = new FileRepositoryServer(fileRepo)
    val executor = constructExecutorClient()
    val registry = RemoteMantikRegistry.empty
    constructWithComponents(repository, fileRepo, fileRepoServer, executor, registry)
  }

  /** Construct a context with a running local stateful services (e.g. the Engine). */
  private def constructWithComponents(
    repository: Repository,
    fileRepository: FileRepository,
    fileRepositoryServer: FileRepositoryServer,
    executor: Executor,
    registry: RemoteMantikRegistry
  )(implicit akkaRuntime: AkkaRuntime): Context = {
    val bridges: Bridges = Bridges.loadFromConfig(akkaRuntime.config)
    val planner = new PlannerImpl(bridges)
    val localRegistry = new LocalMantikRegistryImpl(fileRepository, repository)
    val retriever = new MantikArtifactRetrieverImpl(localRegistry, registry)
    val fileRepositoryServerRemotePresence = new FileRepositoryServerRemotePresence(fileRepositoryServer, executor)
    val clientConfig = ClientConfig(
      remoteFileRepositoryAddress = fileRepositoryServerRemotePresence.assembledRemoteUri()
    )

    val planExecutor = new PlanExecutorImpl(
      fileRepository,
      repository,
      executor,
      retriever,
      clientConfig
    )
    new ContextImpl(localRegistry, planner, planExecutor, registry, retriever)
  }

  private def constructExecutorClient()(implicit akkaRuntime: AkkaRuntime): Executor = {
    val executorUrl = akkaRuntime.config.getString("mantik.executor.client.executorUrl")
    val executor: Executor = new ExecutorClient(executorUrl)
    executor
  }

}
package ai.mantik.planner.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import ai.mantik
import ai.mantik.ds.helper.ZipUtils
import ai.mantik.elements.{ MantikId, Mantikfile }
import ai.mantik.executor.Executor
import ai.mantik.executor.client.ExecutorClient
import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import ai.mantik.planner.impl.exec.PlanExecutorImpl
import ai.mantik.planner.repository.{ FileRepository, MantikArtifact, Repository }
import ai.mantik.planner.utils.{ AkkaRuntime, ComponentBase }
import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes
import akka.stream.scaladsl.FileIO
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.{ Config, ConfigFactory }
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.reflect.ClassTag

private[impl] class ContextImpl(val repository: Repository, val fileRepository: FileRepository, val planner: Planner, val planExecutor: PlanExecutor, shutdownHandle: () => Unit)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with Context {
  private val logger = LoggerFactory.getLogger(getClass)

  private val dbLookupTimeout = Duration.fromNanos(config.getDuration("mantik.planner.dbLookupTimeout").toNanos)
  private val jobTimeout = Duration.fromNanos(config.getDuration("mantik.planner.jobTimeout").toNanos)

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

  private def load[T <: MantikItem](id: MantikId)(implicit classTag: ClassTag[T#DefinitionType]): T = {
    val (artifact, hull) = await(repository.getWithHull(id), dbLookupTimeout)
    artifact.forceMantikfileCast[T#DefinitionType]
    val item = MantikItem.fromMantikArtifact(artifact, hull)
    item.asInstanceOf[T]
  }

  override def execute[T](action: Action[T]): T = {
    val plan = planner.convert(action)
    val result = await(planExecutor.execute(plan), jobTimeout)
    result
  }

  private def await[T](future: Future[T], timeout: FiniteDuration) = {
    Await.result(future, timeout)
  }

  override def pushLocalMantikFile(dir: Path, id: Option[MantikId] = None): MantikId = {
    logger.info(s"Pushing local Mantik file...")
    val file = dir.resolve("Mantikfile")
    val fileContent = FileUtils.readFileToString(file.toFile, StandardCharsets.UTF_8)
    // Parsing
    val mantikfile = Mantikfile.fromYaml(fileContent) match {
      case Left(error) => throw new RuntimeException("Could not parse mantik file", error)
      case Right(ok)   => ok
    }
    val idToUse = id.getOrElse {
      mantikfile.header.id.getOrElse(throw new RuntimeException("Mantikfile has no id and no id is given"))
    }
    val fileId = mantikfile.definition.directory.map { dataDir =>
      // Uploading File Content
      val resolved = dir.resolve(dataDir)
      require(resolved.startsWith(dir), "Data directory may not escape root directory")
      val tempFile = Files.createTempFile("mantik_context", ".zip")
      ZipUtils.zipDirectory(resolved, tempFile)
      val fileStorage = await(fileRepository.requestFileStorage(false), dbLookupTimeout)
      val sink = await(fileRepository.storeFile(fileStorage.fileId, MediaTypes.`application/octet-stream`.value), dbLookupTimeout)
      val source = FileIO.fromPath(tempFile)
      await(source.runWith(sink), dbLookupTimeout)
      tempFile.toFile.delete()
      fileStorage.fileId
    }
    val artifact = MantikArtifact(mantikfile, fileId, idToUse)
    await(repository.store(artifact), dbLookupTimeout)
    logger.info(s"Storing ${artifact.id} done")
    idToUse
  }

  override def shutdown(): Unit = {
    fileRepository.shutdown()
    repository.shutdown()
    shutdownHandle()
  }
}

object ContextImpl {

  def constructForLocalTesting(): Context = {
    implicit val akkaRuntime = AkkaRuntime.createNew()
    constructForLocalTestingWithAkka(
      shutdownMethod = () => akkaRuntime.actorSystem.terminate()
    )
  }

  def constructForLocalTestingWithAkka(shutdownMethod: () => Unit = () => (()))(implicit akkaRuntime: AkkaRuntime): Context = {
    val executorUrl = akkaRuntime.config.getString("mantik.core.executorUrl")

    implicit val ec: ExecutionContext = akkaRuntime.executionContext
    val repository = Repository.create()
    val fileRepo: FileRepository = FileRepository.createFileRepository()
    val bridges: Bridges = Bridges.loadFromConfig(akkaRuntime.config)
    val planner = new PlannerImpl(bridges)
    val executor: Executor = new ExecutorClient(executorUrl)(
      akkaRuntime.actorSystem,
      akkaRuntime.materializer
    )
    val planExecutor = new PlanExecutorImpl(
      fileRepo,
      repository,
      executor,
      "local",
      bridges.dockerConfig)
    new ContextImpl(repository, fileRepo, planner, planExecutor, shutdownMethod)
  }
}
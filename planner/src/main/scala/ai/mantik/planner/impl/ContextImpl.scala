package ai.mantik.planner.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import ai.mantik
import ai.mantik.ds.helper.ZipUtils
import ai.mantik.executor.Executor
import ai.mantik.executor.client.ExecutorClient
import ai.mantik.planner._
import ai.mantik.planner.bridge.Bridges
import ai.mantik.planner.impl.exec.PlanExecutorImpl
import ai.mantik.repository._
import ai.mantik.repository.impl.{ SimpleInMemoryRepository, SimpleTempFileRepository }
import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes
import akka.stream.scaladsl.FileIO
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }

private[impl] class ContextImpl(repository: Repository, fileRepository: FileRepository, planner: Planner, planExecutor: PlanExecutor, shutdownHandle: () => Unit)(implicit ec: ExecutionContext, mat: Materializer) extends Context {
  private val logger = LoggerFactory.getLogger(getClass)

  override def loadDataSet(id: MantikId): DataSet = {
    val (artifact, mf) = await(repository.getAs[DataSetDefinition](id))
    mantik.planner.DataSet(
      mantikArtifactSource(artifact),
      mf
    )
  }

  override def loadTransformation(id: MantikId): Algorithm = {
    val (artifact, mf) = await(repository.getAs[AlgorithmDefinition](id))
    mantik.planner.Algorithm(
      mantikArtifactSource(artifact),
      mf
    )
  }

  override def loadTrainableAlgorithm(id: MantikId): TrainableAlgorithm = {
    val (artifact, mf) = await(repository.getAs[TrainableAlgorithmDefinition](id))
    TrainableAlgorithm(
      mantikArtifactSource(artifact),
      mf
    )
  }

  private def mantikArtifactSource(mantikArtifact: MantikArtifact): Source = {
    mantikArtifact.fileId.map(Source.Loaded).getOrElse(Source.Empty)
  }

  override def execute[T](action: Action[T]): T = {
    val plan = planner.convert(action)
    val result = await(planExecutor.execute(plan))
    result.asInstanceOf[T]
  }

  private def await[T](future: Future[T]) = {
    Await.result(future, 60.seconds)
  }

  override def pushLocalMantikFile(dir: Path, id: Option[MantikId] = None): Unit = {
    logger.info(s"Pushing local Mantik file...")
    val file = dir.resolve("Mantikfile")
    val fileContent = FileUtils.readFileToString(file.toFile, StandardCharsets.UTF_8)
    // Parsing
    val mantikfile = Mantikfile.fromYaml(fileContent) match {
      case Left(error) => throw new RuntimeException("Could not parse mantik file", error)
      case Right(ok)   => ok
    }
    val idToUse = id.getOrElse {
      val name = mantikfile.definition.name.getOrElse(throw new RuntimeException("Mantikfile has no id and no id is given"))
      MantikId(name, mantikfile.definition.version)
    }
    val fileId = mantikfile.definition.directory.map { dataDir =>
      // Uploading File Content
      val resolved = dir.resolve(dataDir)
      require(resolved.startsWith(dir), "Data directory may not escape root directory")
      val tempFile = Files.createTempFile("mantik_context", ".zip")
      ZipUtils.zipDirectory(resolved, tempFile)
      val fileStorage = await(fileRepository.requestFileStorage(false))
      val sink = await(fileRepository.storeFile(fileStorage.fileId, MediaTypes.`application/octet-stream`.value))
      val source = FileIO.fromPath(tempFile)
      await(source.runWith(sink))
      tempFile.toFile.delete()
      fileStorage.fileId
    }
    val artifact = MantikArtifact(mantikfile, fileId, idToUse)
    await(repository.store(artifact))
    logger.info(s"Storing ${artifact.id} done")
  }

  override def shutdown(): Unit = {
    shutdownHandle()
  }
}

object ContextImpl {
  def constructForLocalTesting(): Context = {
    val config = ConfigFactory.load()
    val executorUrl = config.getString("mantik.core.executorUrl")
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    implicit val materializer: Materializer = ActorMaterializer.create(actorSystem)

    val repository = new SimpleInMemoryRepository()
    val fileRepo: FileRepository = new SimpleTempFileRepository(config)
    val bridges: Bridges = Bridges.loadFromConfig(config)
    val planner = new PlannerImpl(bridges)
    val shutdownMethod: () => Unit = () => {
      actorSystem.terminate()
    }
    val executor: Executor = new ExecutorClient(executorUrl)
    val planExecutor = new PlanExecutorImpl(
      config,
      fileRepo,
      repository,
      executor,
      "local",
      "application/x-mantik-bundle",
      bridges.dockerConfig)
    new ContextImpl(repository, fileRepo, planner, planExecutor, shutdownMethod)
  }
}
package ai.mantik.core.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import ai.mantik.core._
import ai.mantik.core.plugins.{ NaturalFormatPlugin, Plugins }
import ai.mantik.ds.helper.ZipUtils
import ai.mantik.executor.Executor
import ai.mantik.executor.client.ExecutorClient
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import ai.mantik.repository.impl.{ SimpleInMemoryRepository, SimpleTempFileRepository }
import ai.mantik.repository._
import akka.http.scaladsl.model.MediaTypes
import akka.stream.scaladsl.FileIO
import org.apache.commons.io.{ FileUtils, FilenameUtils }
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._

private[impl] class ContextImpl(repository: Repository, fileRepository: FileRepository, planner: Planner, planExecutor: PlanExecutor, shutdownHandle: () => Unit)(implicit ec: ExecutionContext, mat: Materializer) extends Context {
  val logger = LoggerFactory.getLogger(getClass)

  override def loadDataSet(id: MantikId): DataSet = {
    val (artefact, mf) = await(repository.getAs[DataSetDefinition](id))
    DataSet(
      mantikArtefactSource(artefact),
      mf
    )
  }

  override def loadTransformation(id: MantikId): Algorithm = {
    val (artefact, mf) = await(repository.getAs[AlgorithmDefinition](id))
    Algorithm(
      mantikArtefactSource(artefact),
      mf
    )
  }

  override def loadTrainableAlgorithm(id: MantikId): TrainableAlgorithm = {
    val (artefact, mf) = await(repository.getAs[TrainableAlgorithmDefinition](id))
    TrainableAlgorithm(
      mantikArtefactSource(artefact),
      mf
    )
  }

  private def mantikArtefactSource(mantikArtefact: MantikArtefact): Source = {
    mantikArtefact.fileId.map(Source.Loaded).getOrElse(Source.Empty)
  }

  override def execute[T](action: Action[T]): T = {
    val plan = await(planner.convert(action))
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
    val artefact = MantikArtefact(mantikfile, fileId, idToUse)
    await(repository.store(artefact))
    logger.info(s"Storing ${artefact.id} done")
  }

  override def shutdown(): Unit = {
    shutdownHandle()
  }
}

object ContextImpl {
  def constructForLocalTesting(): Context = {
    implicit val actorSystem = ActorSystem()
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    implicit val materializer = ActorMaterializer.create(actorSystem)

    val repository = new SimpleInMemoryRepository()
    val fileRepo: FileRepository = new SimpleTempFileRepository()
    val formatPlugins = Plugins.default
    val planner = new PlannerImpl("local", fileRepo, formatPlugins)
    val shutdownMethod: () => Unit = () => {
      actorSystem.terminate()
    }
    val executor: Executor = new ExecutorClient("http://localhost:8080")
    val planExecutor = new PlanExecutorImpl(fileRepo, repository, executor)
    new ContextImpl(repository, fileRepo, planner, planExecutor, shutdownMethod)
  }
}
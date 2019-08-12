package ai.mantik.planner.repository.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{ AkkaRuntime, ComponentBase }
import ai.mantik.ds.helper.ZipUtils
import ai.mantik.elements.{ ItemId, MantikId, Mantikfile }
import ai.mantik.planner.impl.ReferencingItemLoader
import ai.mantik.planner.repository._
import akka.stream.scaladsl.{ FileIO, Source }
import akka.util.ByteString
import javax.inject.{ Inject, Singleton }
import org.apache.commons.io.FileUtils

import scala.concurrent.Future

/** Responsible for pulling/pushing Mantik Artifacts from local repository and remote registry. */
@Singleton
private[mantik] class MantikArtifactRetrieverImpl @Inject() (
    repository: Repository,
    fileRepository: FileRepository,
    registry: MantikRegistry
)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with MantikArtifactRetriever {

  private val dbLookupTimeout = config.getFiniteDuration("mantik.planner.dbLookupTimeout")
  private val registryTimeout = config.getFiniteDuration("mantik.planner.registryTimeout")
  private val fileTransferTimeout = config.getFiniteDuration("mantik.planner.fileTransferTimeout")

  /** ReferencingItemLoader for MantikArtifacts. */
  private class ReferencingMantikArtifactLoader(loader: MantikId => Future[MantikArtifact]) extends ReferencingItemLoader[MantikId, MantikArtifact](
    loader,
    _.mantikfile.definition.referencedItems
  )

  private val registryLoader = new ReferencingMantikArtifactLoader(
    registryGet
  )

  private val repositoryLoader = new ReferencingMantikArtifactLoader(localRepoGet)

  private def localRepoGet(mantikId: MantikId): Future[MantikArtifact] =
    FutureHelper.addTimeout(repository.get(mantikId), "Loading Mantikfile from local repository", dbLookupTimeout)

  private def registryGet(mantikId: MantikId): Future[MantikArtifact] =
    FutureHelper.addTimeout(registry.get(mantikId), "Loading Mantikfile from remote Registry", registryTimeout)

  override def pull(id: MantikId): Future[MantikArtifactWithHull] = {
    logger.info(s"Pulling ${id}")
    for {
      items <- registryLoader.loadWithHull(id)
      local <- pullRemoteItemsToLocal(items)
    } yield (
      (local.head -> local.tail)
    )
  }

  override def get(id: MantikId): Future[MantikArtifactWithHull] = {
    getLocal(id).recoverWith {
      case n: Errors.NotFoundException =>
        logger.info(s"${id} not available locally, pulling...")
        pull(id)
    }
  }

  override def getLocal(id: MantikId): Future[MantikArtifactWithHull] = {
    repositoryLoader.loadWithHull(id).map { items =>
      items.head -> items.tail
    }
  }

  override def push(id: MantikId): Future[MantikArtifactWithHull] = {
    for {
      items <- repositoryLoader.loadWithHull(id)
      remote <- pushLocalItemsToRemote(items)
    } yield {
      remote.head -> remote.tail
    }
  }

  override def addLocalDirectoryToRepository(dir: Path, id: Option[MantikId] = None): Future[MantikArtifact] = {
    logger.info(s"Adding local Directory ${dir} with Mantikfile")
    val file = dir.resolve("Mantikfile")
    val fileContent = FileUtils.readFileToString(file.toFile, StandardCharsets.UTF_8)
    // Parsing
    val mantikfile = Mantikfile.fromYaml(fileContent) match {
      case Left(error) => throw new IllegalArgumentException("Could not parse mantik file", error)
      case Right(ok)   => ok
    }
    val idToUse = id.getOrElse {
      mantikfile.header.id.getOrElse(throw new IllegalArgumentException("Mantikfile has no id and no id is given"))
    }
    val itemId = ItemId.generate()
    val fileIdFuture: Future[Option[String]] = mantikfile.definition.directory.map { dataDir =>
      // Uploading File Content
      val resolved = dir.resolve(dataDir)
      require(resolved.startsWith(dir), "Data directory may not escape root directory")
      val tempFile = Files.createTempFile("mantik_context", ".zip")
      ZipUtils.zipDirectory(resolved, tempFile)
      for {
        fileStorage <- FutureHelper.addTimeout(fileRepository.requestFileStorage(false), "Requesting Storage", dbLookupTimeout)
        sink <- FutureHelper.addTimeout(fileRepository.storeFile(fileStorage.fileId, ContentTypes.ZipFileContentType), "Requesting Storage sink", dbLookupTimeout)
        source = FileIO.fromPath(tempFile)
        _ <- FutureHelper.addTimeout(source.runWith(sink), "Uploading File", fileTransferTimeout)
      } yield Some(fileStorage.fileId)
    }.getOrElse(Future.successful(None))

    for {
      maybeFileid <- fileIdFuture
      artifact = MantikArtifact(mantikfile, maybeFileid, idToUse, itemId)
      _ <- FutureHelper.addTimeout(repository.store(artifact), "Storing Artifact", dbLookupTimeout)
    } yield {
      logger.info(s"Stored ${artifact.id} done, itemId=${itemId}, fileId=${artifact.fileId}")
      artifact
    }
  }

  private def pullRemoteItemsToLocal(remote: Seq[MantikArtifact]): Future[Seq[MantikArtifact]] = {
    Future.sequence(remote.map(pullRemoteItemToLocal))
  }

  private def pullRemoteItemToLocal(remote: MantikArtifact): Future[MantikArtifact] = {
    logger.debug(s"Pulling remote item ${remote.id}")

    val localExistanceFuture: Future[Option[MantikArtifact]] =
      localRepoGet(MantikId.anonymous(remote.itemId))
        .map(Some(_))
        .recover {
          case e: Errors.NotFoundException => None
        }

    localExistanceFuture.flatMap {
      case Some(existant) =>
        logger.info(s"${remote.itemId} already exists, ensuring id ${remote.id}")
        FutureHelper.addTimeout(repository.ensureMantikId(existant.itemId, remote.id), "Tagging MantikArtifact", dbLookupTimeout).map { _ =>
          val updated = existant.copy(
            id = remote.id
          )
          updated
        }
      case None =>
        logger.info(s"${remote.id} doesn't exist locally, pulling completely")
        // Locally not existing...
        for {
          maybeLocalFileId <- forwardPayloadToInternalRepositoryIfExists(remote.fileId)
          localMantikArtifact = remote.copy(fileId = maybeLocalFileId)
          _ <- FutureHelper.addTimeout(repository.store(localMantikArtifact), "Adding Local MantikArtifact", dbLookupTimeout)
        } yield {
          localMantikArtifact
        }
    }
  }

  private def forwardPayloadToInternalRepositoryIfExists(remoteFileId: Option[String]): Future[Option[String]] = {
    remoteFileId match {
      case Some(remoteFileId) => forwardPayloadToInternalRepository(remoteFileId).map(Some(_))
      case None               => Future.successful(None)
    }
  }

  /** Forward a remote file to the local file repository, returning the local file id. */
  private def forwardPayloadToInternalRepository(remoteFileId: String): Future[String] = {
    for {
      fileHandle <- FutureHelper.addTimeout(fileRepository.requestFileStorage(false), "Requesting Storage", dbLookupTimeout)
      (contentType, remoteSource) <- FutureHelper.addTimeout(registry.getPayload(remoteFileId), "Requesting Payload", registryTimeout)
      fileSink <- fileRepository.storeFile(fileHandle.fileId, contentType)
      _ <- FutureHelper.addTimeout(remoteSource.runWith(fileSink), "Copying remote to local file", fileTransferTimeout)
    } yield {
      fileHandle.fileId
    }
  }

  /**
   * Push multiple local items to remote.
   * Local must be ordered (dependencies last)
   */
  private def pushLocalItemsToRemote(local: Seq[MantikArtifact]): Future[Seq[MantikArtifact]] = {
    // Remote may check for dependencies
    val reversed = local.reverse
    FutureHelper.afterEachOther(reversed)(pushLocalItemToRemote).map(_.reverse)
  }

  /** Push a single local item to remote registry. */
  private def pushLocalItemToRemote(local: MantikArtifact): Future[MantikArtifact] = {
    // Does it exist?
    val maybeExistsFuture = registryGet(MantikId.anonymous(local.itemId)).map(Some(_)).recover {
      case e: Errors.NotFoundException => None
    }
    maybeExistsFuture.flatMap {
      case Some(existant) =>
        logger.info(s"${local.id} exists remote, tagging it.")
        FutureHelper.addTimeout(
          registry.ensureMantikId(existant.itemId, local.id), "Tagging MantikArtifact", registryTimeout
        ).map { _ =>
            val updated = existant.copy(
              id = local.id
            )
            updated
          }
      case None =>
        logger.info(s"${local.id} doesn't exist remotely, uploading...")
        for {
          payload <- local.fileId match {
            case None         => Future.successful(None)
            case Some(fileId) => localFileSource(fileId).map(Some(_))
          }
          response <- FutureHelper.addTimeout(
            registry.addMantikArtifact(local, payload), "Uploading Mantikfile", fileTransferTimeout
          )
        } yield {
          response
        }
    }
  }

  /**
   * Forward a local payload to a remote repository.
   * Returns content type and file source
   */
  private def localFileSource(fileId: String): Future[(String, Source[ByteString, _])] = {
    for {
      fileInfo <- fileRepository.requestFileGet(fileId)
      fileSource <- fileRepository.loadFile(fileId)
    } yield {
      // mm, content type missing?!
      val contentType = fileInfo.contentType.getOrElse {
        logger.warn(s"No content type for local file ${fileId}, assuming zip")
        ContentTypes.ZipFileContentType
      }
      contentType -> fileSource
    }
  }
}

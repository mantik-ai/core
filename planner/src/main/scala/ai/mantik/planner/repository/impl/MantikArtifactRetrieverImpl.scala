package ai.mantik.planner.repository.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ds.helper.ZipUtils
import ai.mantik.elements.{ItemId, MantikId, Mantikfile}
import ai.mantik.planner.impl.ReferencingItemLoader
import ai.mantik.planner.repository._
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.apache.commons.io.FileUtils
import cats.implicits._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/** Responsible for pulling/pushing Mantik Artifacts from local repository and remote registry. */
@Singleton
private[mantik] class MantikArtifactRetrieverImpl @Inject() (
    localMantikRegistry: LocalMantikRegistry,
    remoteMantikRegistry: RemoteMantikRegistry,
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
    FutureHelper.addTimeout(localMantikRegistry.get(mantikId), "Loading Mantikfile from local repository", dbLookupTimeout)

  private def registryGet(mantikId: MantikId): Future[MantikArtifact] =
    FutureHelper.addTimeout(remoteMantikRegistry.get(mantikId), "Loading Mantikfile from remote Registry", registryTimeout)

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

    val payloadSource: Option[(String, Source[ByteString, _])] = mantikfile.definition.directory.map { dataDir =>
      val resolved = dir.resolve(dataDir)
      require(resolved.startsWith(dir), "Data directory may not escape root directory")
      val tempFile = Files.createTempFile("mantik_context", ".zip")
      ZipUtils.zipDirectory(resolved, tempFile)
      val source = FileIO.fromPath(tempFile)
      ContentTypes.ZipFileContentType -> source
    }

    val artifact =  MantikArtifact(mantikfile, None, idToUse, itemId)
    val timeout = if (payloadSource.isDefined){
      fileTransferTimeout
    } else {
      dbLookupTimeout
    }
    FutureHelper.addTimeout(
      localMantikRegistry.addMantikArtifact(artifact, payloadSource), "Uploading Artifact", timeout
    ).map { generatedArtifact =>
      logger.info(s"Stored ${artifact.id} done, itemId=${itemId}, fileId=${artifact.fileId}")
      generatedArtifact
    }
  }

  private def pullRemoteItemsToLocal(remote: Seq[MantikArtifact]): Future[Seq[MantikArtifact]] = {
    Future.sequence(remote.map(pullRemoteItemToLocal))
  }

  private def pullRemoteItemToLocal(remote: MantikArtifact): Future[MantikArtifact] = {
    copyItem(
      "Pulling",
      remote,
      remoteMantikRegistry,
      localMantikRegistry,
      fileTransferTimeout,
      dbLookupTimeout
    )
  }

  /** Copy an item to the `to` Registry. */
  private def copyItem(
    operationName: String,
    fromArtifact: MantikArtifact,
    from: MantikRegistry,
    to: MantikRegistry,
    fileTransferTimeout: FiniteDuration,
    changeTimeout: FiniteDuration
  ): Future[MantikArtifact] = {
    logger.debug(s"${operationName} ${fromArtifact.id}")

    val existing = to.maybeGet(MantikId.anonymous(fromArtifact.itemId))

    existing.flatMap {
      case Some(existant) =>
        logger.info(s"${fromArtifact.itemId} already exists, ensuring id ${fromArtifact.id}")
        FutureHelper.addTimeout(
          to.ensureMantikId(existant.itemId, fromArtifact.id), "Tagging", changeTimeout
        ).map { _ =>
          existant.copy(id = fromArtifact.id)
        }
      case None =>
        logger.debug(s"${fromArtifact.itemId} doesn't exist yet, pulling completely")
        val timeout = if (fromArtifact.fileId.isDefined) {
          fileTransferTimeout
        } else {
          changeTimeout
        }
        for {
          source <- FutureHelper.addTimeout(fromArtifact.fileId.map(from.getPayload).sequence, "Getting File", timeout)
          localArtifact <- FutureHelper.addTimeout(to.addMantikArtifact(fromArtifact, source), "Storing Artifact", timeout)
        } yield localArtifact
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
    copyItem(
      "pushing",
      local,
      localMantikRegistry,
      remoteMantikRegistry,
      fileTransferTimeout,
      registryTimeout
    )
  }
}
